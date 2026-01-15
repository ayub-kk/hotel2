package ru.mifi.booking.bookingservice.repository;

import ru.mifi.booking.bookingservice.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);

    Page<Booking> findByUserId(Long userId, Pageable pageable);
}