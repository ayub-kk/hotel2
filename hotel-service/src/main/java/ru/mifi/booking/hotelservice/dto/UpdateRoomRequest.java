package ru.mifi.booking.hotelservice.dto;

/**
 * DTO для частичного обновления номера (PATCH).
 *
 * <p>
 * Поля могут быть null — это значит "не обновлять".
 * </p>
 */
public record UpdateRoomRequest(
        String number,
        Boolean available
) {}
