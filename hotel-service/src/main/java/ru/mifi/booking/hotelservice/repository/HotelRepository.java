package ru.mifi.booking.hotelservice.repository;

import ru.mifi.booking.hotelservice.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, Long> {}

