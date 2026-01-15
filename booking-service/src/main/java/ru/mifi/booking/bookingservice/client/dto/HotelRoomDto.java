package ru.mifi.booking.bookingservice.client.dto;

/**
 * DTO номера из hotel-service.
 *
 * <p>
 * Я храню только те поля, которые реально возвращает hotel-service в RoomDto,
 * чтобы booking-service мог делать autoSelect через /api/rooms/recommend.
 * </p>
 */
public record HotelRoomDto(
        Long id,
        Long hotelId,
        String number,
        boolean available,
        long timesBooked
) {
}
