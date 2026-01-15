package ru.mifi.booking.bookingservice.client.dto;

import java.time.LocalDate;

/**
 * DTO для вызова internal endpoint hotel-service:
 * POST /api/rooms/{id}/confirm-availability
 */
public record ConfirmAvailabilityRequest(
        LocalDate startDate,
        LocalDate endDate,
        String bookingId,
        String requestId
) {
}
