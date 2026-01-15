package ru.mifi.booking.hotelservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO отеля для REST API.
 */
public record HotelDto(
        Long id,
        @NotBlank String name,
        @NotBlank String address
) {}

