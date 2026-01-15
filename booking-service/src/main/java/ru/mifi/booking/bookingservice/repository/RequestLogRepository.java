package ru.mifi.booking.bookingservice.repository;

import ru.mifi.booking.bookingservice.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {
    Optional<RequestLog> findByRequestId(String requestId);
}

