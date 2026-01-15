package ru.mifi.booking.bookingservice.dto;

import ru.mifi.booking.bookingservice.entity.BookingStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            boolean autoSelect,
            Long roomId
    ) {}

    public record BookingResponse(
            Long id,
            String bookingUid,
            Long roomId,
            LocalDate startDate,
            LocalDate endDate,
            BookingStatus status
    ) {}
}