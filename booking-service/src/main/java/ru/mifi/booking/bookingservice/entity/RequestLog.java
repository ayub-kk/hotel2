package ru.mifi.booking.bookingservice.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "request_log",
        indexes = { @Index(name = "idx_request_id", columnList = "requestId", unique = true) }
)
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String requestId;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public RequestLog() {
    }

    public RequestLog(String requestId, OffsetDateTime createdAt) {
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
