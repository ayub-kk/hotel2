package ru.mifi.booking.hotelservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.mifi.booking.hotelservice.dto.HotelDto;
import ru.mifi.booking.hotelservice.dto.UpdateHotelRequest;
import ru.mifi.booking.hotelservice.service.HotelService;

import java.util.List;

/**
 * Контроллер CRUD-операций по отелям.
 */
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/hotels")
public class HotelController {

    private final HotelService hotelService;

    public HotelController(HotelService hotelService) {
        this.hotelService = hotelService;
    }

    /**
     * ADMIN: создать отель.
     *
     * @param dto dto отеля (name, address)
     * @return созданный отель
     */
    @PostMapping
    public HotelDto create(@Valid @RequestBody HotelDto dto) {
        return hotelService.create(dto);
    }

    /**
     * USER: список отелей.
     */
    @GetMapping
    public List<HotelDto> list() {
        return hotelService.list();
    }

    /**
     * USER: получить отель по id.
     *
     * @param id идентификатор отеля
     * @return отель
     */
    @GetMapping("/{id}")
    public HotelDto get(@PathVariable("id") Long id) {
        return hotelService.get(id);
    }

    /**
     * ADMIN: частично обновить отель (PATCH).
     *
     * @param id  идентификатор отеля
     * @param req запрос обновления (поля могут быть null)
     * @return обновлённый отель
     */
    @PatchMapping("/{id}")
    public HotelDto patch(@PathVariable("id") Long id, @RequestBody UpdateHotelRequest req) {
        return hotelService.update(id, req);
    }

    /**
     * ADMIN: удалить отель.
     *
     * @param id идентификатор отеля
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        hotelService.delete(id);
    }
}