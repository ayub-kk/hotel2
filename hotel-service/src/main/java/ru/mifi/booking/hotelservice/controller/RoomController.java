package ru.mifi.booking.hotelservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.slf4j.MDC;
import ru.mifi.booking.hotelservice.dto.ConfirmAvailabilityRequest;
import ru.mifi.booking.hotelservice.dto.RoomDto;
import ru.mifi.booking.hotelservice.dto.RoomStatsDto;
import ru.mifi.booking.hotelservice.dto.UpdateRoomRequest;
import ru.mifi.booking.hotelservice.service.HotelService;
import ru.mifi.booking.hotelservice.service.RoomService;

import java.time.LocalDate;
import java.util.List;

/**
 * Контроллер работы с номерами (rooms).
 *
 * <p>
 * Публичные методы предоставляют просмотр доступности номеров.
 * Внутренние методы используются Booking Service для confirm/release в рамках саги.
 * </p>
 */
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final HotelService hotelService;
    private final RoomService roomService;

    /**
     * Конструктор контроллера.
     *
     * @param hotelService сервис работы с отелями
     * @param roomService  сервис работы с номерами
     */
    public RoomController(HotelService hotelService, RoomService roomService) {
        this.hotelService = hotelService;
        this.roomService = roomService;
    }

    /**
     * ADMIN: добавить номер в отель.
     *
     * @param hotelId   идентификатор отеля
     * @param number    номер комнаты (строка, например "101A")
     * @param available доступность комнаты (по умолчанию true)
     * @return созданный номер
     */
    @PostMapping
    public RoomDto add(
            @RequestParam("hotelId") Long hotelId,
            @RequestParam("number") String number,
            @RequestParam(value = "available", defaultValue = "true") boolean available
    ) {
        return roomService.addRoom(hotelService.getOrThrow(hotelId), number, available);
    }

    /**
     * USER: список свободных номеров на период.
     *
     * @param start дата начала (ISO-8601, например 2025-12-25)
     * @param end   дата окончания (ISO-8601, например 2025-12-28)
     * @return список доступных номеров
     */
    @GetMapping
    public List<RoomDto> list(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return roomService.listAvailable(start, end);
    }

    /**
     * USER: рекомендованные номера на период (сортировка: timesBooked asc, затем id asc).
     *
     * @param start дата начала (ISO-8601)
     * @param end   дата окончания (ISO-8601)
     * @return список рекомендованных номеров
     */
    @GetMapping("/recommend")
    public List<RoomDto> recommend(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return roomService.recommend(start, end);
    }

    /**
     * USER: получить номер по id.
     *
     * @param id идентификатор номера
     * @return номер
     */
    @GetMapping("/{id}")
    public RoomDto get(@PathVariable("id") Long id) {
        return roomService.get(id);
    }

    /**
     * ADMIN: частично обновить номер.
     *
     * @param id  идентификатор номера
     * @param req запрос (number/available могут быть null)
     * @return обновлённый номер
     */
    @PatchMapping("/{id}")
    public RoomDto patch(@PathVariable("id") Long id, @RequestBody UpdateRoomRequest req) {
        return roomService.update(id, req);
    }

    /**
     * ADMIN: удалить номер.
     *
     * @param id идентификатор номера
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        roomService.delete(id);
    }

    /**
     * ADMIN: статистика загруженности номеров отеля за период.
     *
     * @param hotelId идентификатор отеля
     * @param start   дата начала (ISO-8601)
     * @param end     дата окончания (ISO-8601)
     * @return статистика по каждому номеру
     */
    @GetMapping("/stats")
    public List<RoomStatsDto> stats(
            @RequestParam("hotelId") Long hotelId,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        // Если отеля нет — хочу вернуть 404 заранее.
        hotelService.getOrThrow(hotelId);

        return roomService.stats(hotelId, start, end);
    }

    /**
     * INTERNAL: подтвердить доступность номера на период (временная блокировка).
     *
     * @param id  идентификатор номера
     * @param req запрос подтверждения доступности
     */
    @PostMapping("/{id}/confirm-availability")
    public void confirm(
            @PathVariable("id") Long id,
            @Valid @RequestBody ConfirmAvailabilityRequest req
    ) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingUid", req.bookingId())) {
            roomService.confirmAvailability(id, req);
        }
    }

    /**
     * INTERNAL: компенсирующее действие — снять блокировку.
     *
     * @param id        идентификатор номера
     * @param bookingId идентификатор бронирования, для которого снимется блокировка
     */
    @PostMapping("/{id}/release")
    public void release(
            @PathVariable("id") Long id,
            @RequestParam("bookingId") String bookingId
    ) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable("bookingUid", bookingId)) {
            roomService.release(id, bookingId);
        }
    }
}