package ru.mifi.booking.hotelservice.dto;

/**
 * DTO для частичного обновления отеля (PATCH).
 *
 * <p>
 * Поля могут быть null — это значит "не обновлять".
 * Валидацию "не пусто" я делаю в сервисе, чтобы корректно обработать null/blank.
 * </p>
 */
public record UpdateHotelRequest(
        String name,
        String address
) {}
