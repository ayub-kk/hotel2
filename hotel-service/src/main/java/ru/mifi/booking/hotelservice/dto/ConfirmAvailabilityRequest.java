package ru.mifi.booking.hotelservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Запрос на временную блокировку номера под бронирование.
 */
public record ConfirmAvailabilityRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotBlank String bookingId,
        @NotBlank String requestId
) {}

