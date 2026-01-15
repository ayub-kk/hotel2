package ru.mifi.booking.bookingservice.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        @NotNull Long id,
        String name,
        String email,
        String password,
        String role
) {}
