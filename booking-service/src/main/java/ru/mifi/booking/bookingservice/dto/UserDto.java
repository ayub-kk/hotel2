package ru.mifi.booking.bookingservice.dto;

public record UserDto(
        Long id,
        String name,
        String email,
        String role
) {}
