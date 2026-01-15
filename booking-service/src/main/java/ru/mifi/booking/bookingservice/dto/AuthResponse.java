package ru.mifi.booking.bookingservice.dto;

public record AuthResponse(
        Long userId,
        String token,
        String role
) {}
