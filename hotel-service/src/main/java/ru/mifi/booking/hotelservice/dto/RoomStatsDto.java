package ru.mifi.booking.hotelservice.dto;

/**
 * Статистика загруженности номера за период.
 */
public record RoomStatsDto(
        Long roomId,
        Long hotelId,
        String number,
        long timesBooked,
        long locksCountInRange,
        long bookedDaysInRange
) {}
