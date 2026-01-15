package ru.mifi.booking.hotelservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mifi.booking.common.exception.BadRequestException;
import ru.mifi.booking.common.exception.ConflictException;
import ru.mifi.booking.common.exception.NotFoundException;
import ru.mifi.booking.hotelservice.dto.ConfirmAvailabilityRequest;
import ru.mifi.booking.hotelservice.dto.RoomDto;
import ru.mifi.booking.hotelservice.dto.RoomStatsDto;
import ru.mifi.booking.hotelservice.dto.UpdateRoomRequest;
import ru.mifi.booking.hotelservice.entity.Hotel;
import ru.mifi.booking.hotelservice.entity.Room;
import ru.mifi.booking.hotelservice.entity.RoomLock;
import ru.mifi.booking.hotelservice.repository.RoomLockRepository;
import ru.mifi.booking.hotelservice.repository.RoomRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final RoomLockRepository roomLockRepository;

    public RoomService(RoomRepository roomRepository, RoomLockRepository roomLockRepository) {
        this.roomRepository = roomRepository;
        this.roomLockRepository = roomLockRepository;
    }

    /**
     * Добавить номер в отель.
     */
    public RoomDto addRoom(Hotel hotel, String number, boolean available) {
        Room room = new Room(null, hotel, number, available, 0);
        Room saved = roomRepository.save(room);
        return toDto(saved);
    }

    /**
     * USER: получить номер по id.
     *
     * @param id идентификатор номера
     * @return номер
     */
    public RoomDto get(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Room " + id + " not found"));
        return toDto(room);
    }

    /**
     * USER: список доступных номеров на период.
     */
    public List<RoomDto> listAvailable(LocalDate start, LocalDate end) {
        validateRange(start, end);

        return roomRepository.findAllAvailable().stream()
                .filter(r -> roomLockRepository.findOverlaps(r, start, end).isEmpty())
                .map(this::toDto)
                .toList();
    }

    /**
     * USER: рекомендованные номера: те же доступные, но отсортированы по timesBooked (по возрастанию), затем по id.
     */
    public List<RoomDto> recommend(LocalDate start, LocalDate end) {
        return listAvailable(start, end).stream()
                .sorted(Comparator.comparingLong(RoomDto::timesBooked).thenComparing(RoomDto::id))
                .toList();
    }

    /**
     * ADMIN: частично обновить номер (PATCH).
     *
     * @param id  идентификатор номера
     * @param req запрос обновления
     * @return обновлённый номер
     */
    @Transactional
    public RoomDto update(Long id, UpdateRoomRequest req) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Room " + id + " not found"));

        boolean changed = false;

        if (req != null && req.number() != null) {
            String number = req.number();
            if (number.isBlank()) {
                throw new BadRequestException("number must not be blank");
            }
            room.setNumber(number);
            changed = true;
        }

        if (req != null && req.available() != null) {
            room.setAvailable(req.available());
            changed = true;
        }

        if (!changed) {
            throw new BadRequestException("At least one field must be provided for PATCH");
        }

        return toDto(room);
    }

    /**
     * ADMIN: удалить номер вместе с блокировками.
     *
     * @param id идентификатор номера
     */
    @Transactional
    public void delete(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Room " + id + " not found"));

        roomLockRepository.deleteAllByRoom_Id(room.getId());
        roomRepository.delete(room);
    }

    /**
     * ADMIN: статистика загруженности номеров по отелю за период.
     *
     * @param hotelId идентификатор отеля
     * @param start   начало периода
     * @param end     конец периода
     * @return статистика по каждому номеру отеля
     */
    public List<RoomStatsDto> stats(Long hotelId, LocalDate start, LocalDate end) {
        validateRange(start, end);

        List<Room> rooms = roomRepository.findAllByHotelId(hotelId);
        List<RoomLock> locks = roomLockRepository.findOverlapsInHotel(hotelId, start, end);

        Map<Long, List<RoomLock>> locksByRoomId = locks.stream()
                .collect(Collectors.groupingBy(l -> l.getRoom().getId()));

        return rooms.stream()
                .sorted(Comparator.comparingLong(Room::getId))
                .map(room -> {
                    List<RoomLock> roomLocks = locksByRoomId.getOrDefault(room.getId(), List.of());

                    long locksCount = roomLocks.size();
                    long bookedDays = roomLocks.stream()
                            .mapToLong(l -> overlapDays(l.getStartDate(), l.getEndDate(), start, end))
                            .sum();

                    return new RoomStatsDto(
                            room.getId(),
                            room.getHotel().getId(),
                            room.getNumber(),
                            room.getTimesBooked(),
                            locksCount,
                            bookedDays
                    );
                })
                .toList();
    }

    /**
     * INTERNAL: подтвердить доступность (временная блокировка).
     * Идемпотентность: если requestId уже был — просто выходим без ошибки.
     */
    @Transactional
    public void confirmAvailability(Long roomId, ConfirmAvailabilityRequest req) {
        validateRange(req.startDate(), req.endDate());

        // 1) Блокируем room строку, чтобы сериализовать конкурентные confirm на один и тот же roomId.
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new NotFoundException("Room " + roomId + " not found"));

        // 2) Идемпотентность по requestId.
        //    Проверку делаю ПОСЛЕ лока, чтобы снизить риск гонки «check-then-insert».
        if (roomLockRepository.findByRequestId(req.requestId()).isPresent()) {
            log.debug("confirm-availability idempotent hit: roomId={}, requestId={}", roomId, req.requestId());
            return;
        }

        if (!room.isAvailable()) {
            throw new ConflictException("Room is not operational");
        }

        if (!roomLockRepository.findOverlaps(room, req.startDate(), req.endDate()).isEmpty()) {
            throw new ConflictException("Room is not available for this period");
        }

        RoomLock lock = new RoomLock(null, room, req.startDate(), req.endDate(), req.bookingId(), req.requestId());
        try {
            roomLockRepository.save(lock);
        } catch (DataIntegrityViolationException ex) {
            // В конкурентных сценариях возможна ситуация, когда requestId «влетел» параллельно.
            // Тогда считаю это успешной идемпотентной обработкой.
            if (roomLockRepository.findByRequestId(req.requestId()).isPresent()) {
                log.debug("confirm-availability idempotent after save-race: roomId={}, requestId={}", roomId, req.requestId());
                return;
            }
            throw ex;
        }

        // метрика справедливости: увеличиваем при подтверждении доступности
        room.setTimesBooked(room.getTimesBooked() + 1);
        // save не обязателен, если Room является managed-entity в текущей транзакции.
    }

    /**
     * INTERNAL: компенсирующее действие — снять блокировку.
     *
     * <p>
     * Важный момент fairness-метрики:
     * если блокировку реально нашли и удалили, то уменьшаем timesBooked на 1 (не ниже 0),
     * чтобы отменённые брони не "забивали" статистику рекомендаций.
     * </p>
     */
    @Transactional
    public void release(Long roomId, String bookingId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room " + roomId + " not found"));

        roomLockRepository.findByBookingId(bookingId)
                .ifPresent(lock -> {
                    // Доп.страховка: bookingId уникален, но проверю, что lock относится к нашему roomId.
                    if (lock.getRoom() != null && roomId.equals(lock.getRoom().getId())) {
                        roomLockRepository.delete(lock);

                        long current = room.getTimesBooked();
                        room.setTimesBooked(Math.max(0, current - 1));
                    }
                });
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BadRequestException("start and end must be provided");
        }
        if (!start.isBefore(end)) {
            throw new BadRequestException("start must be before end");
        }
    }

    private long overlapDays(LocalDate lockStart, LocalDate lockEnd, LocalDate start, LocalDate end) {
        LocalDate s = lockStart.isAfter(start) ? lockStart : start;
        LocalDate e = lockEnd.isBefore(end) ? lockEnd : end;
        long days = ChronoUnit.DAYS.between(s, e);
        return Math.max(0, days);
    }

    private RoomDto toDto(Room room) {
        return new RoomDto(
                room.getId(),
                room.getHotel().getId(),
                room.getNumber(),
                room.isAvailable(),
                room.getTimesBooked()
        );
    }
}
