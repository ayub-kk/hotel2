package ru.mifi.booking.bookingservice.service;

import ru.mifi.booking.bookingservice.entity.RequestLog;
import ru.mifi.booking.common.exception.ConflictException;
import ru.mifi.booking.bookingservice.repository.RequestLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class IdempotencyService {

    private final RequestLogRepository requestLogRepository;

    public IdempotencyService(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }

    @Transactional
    public void rememberOrThrow(String requestId) {
        try {
            requestLogRepository.saveAndFlush(new RequestLog(requestId, OffsetDateTime.now()));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Duplicate request: X-Request-Id=" + requestId);
        }
    }
}

