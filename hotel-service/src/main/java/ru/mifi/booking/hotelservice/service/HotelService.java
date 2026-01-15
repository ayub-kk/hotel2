package ru.mifi.booking.hotelservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mifi.booking.common.exception.BadRequestException;
import ru.mifi.booking.common.exception.NotFoundException;
import ru.mifi.booking.hotelservice.dto.HotelDto;
import ru.mifi.booking.hotelservice.dto.UpdateHotelRequest;
import ru.mifi.booking.hotelservice.entity.Hotel;
import ru.mifi.booking.hotelservice.entity.Room;
import ru.mifi.booking.hotelservice.repository.HotelRepository;
import ru.mifi.booking.hotelservice.repository.RoomLockRepository;
import ru.mifi.booking.hotelservice.repository.RoomRepository;

import java.util.List;

/**
 * Сервис CRUD-операций по отелям.
 */
@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RoomLockRepository roomLockRepository;

    public HotelService(
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            RoomLockRepository roomLockRepository
    ) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.roomLockRepository = roomLockRepository;
    }

    /**
     * Создать отель.
     *
     * @param dto dto отеля
     * @return созданный отель
     */
    public HotelDto create(HotelDto dto) {
        Hotel saved = hotelRepository.save(new Hotel(null, dto.name(), dto.address()));
        return toDto(saved);
    }

    /**
     * Получить список отелей.
     *
     * @return список отелей
     */
    public List<HotelDto> list() {
        return hotelRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Получить отель по id.
     *
     * @param id идентификатор отеля
     * @return отель
     */
    public HotelDto get(Long id) {
        return toDto(getOrThrow(id));
    }

    /**
     * Частичное обновление отеля (PATCH).
     *
     * @param id  идентификатор отеля
     * @param req запрос обновления
     * @return обновлённый отель
     */
    @Transactional
    public HotelDto update(Long id, UpdateHotelRequest req) {
        Hotel hotel = getOrThrow(id);

        boolean changed = false;

        if (req != null && req.name() != null) {
            String name = req.name();
            if (name.isBlank()) {
                throw new BadRequestException("name must not be blank");
            }
            hotel.setName(name);
            changed = true;
        }

        if (req != null && req.address() != null) {
            String address = req.address();
            if (address.isBlank()) {
                throw new BadRequestException("address must not be blank");
            }
            hotel.setAddress(address);
            changed = true;
        }

        if (!changed) {
            throw new BadRequestException("At least one field must be provided for PATCH");
        }

        return toDto(hotel);
    }

    /**
     * Удалить отель.
     *
     * <p>
     * Я удаляю связанные комнаты и их блокировки, чтобы не нарушить ссылочную целостность.
     * </p>
     *
     * @param id идентификатор отеля
     */
    @Transactional
    public void delete(Long id) {
        Hotel hotel = getOrThrow(id);

        List<Room> rooms = roomRepository.findAllByHotelId(id);
        for (Room room : rooms) {
            roomLockRepository.deleteAllByRoom_Id(room.getId());
        }

        roomRepository.deleteAll(rooms);
        hotelRepository.delete(hotel);
    }

    /**
     * Получить отель или выбросить 404 Not Found.
     *
     * @param id идентификатор отеля
     * @return сущность Hotel
     */
    public Hotel getOrThrow(Long id) {
        return hotelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Hotel " + id + " not found"));
    }

    private HotelDto toDto(Hotel hotel) {
        return new HotelDto(hotel.getId(), hotel.getName(), hotel.getAddress());
    }
}
