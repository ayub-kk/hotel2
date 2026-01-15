package ru.mifi.booking.hotelservice.dto;

/**
 * DTO номера.
 */
public record RoomDto(
        Long id,
        Long hotelId,
        String number,
        boolean available,
        long timesBooked
) {}

