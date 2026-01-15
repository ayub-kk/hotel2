package ru.mifi.booking.bookingservice.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "booking",
        indexes = {
                @Index(name = "idx_booking_user", columnList = "userId"),
                @Index(name = "idx_booking_room", columnList = "roomId"),
                @Index(name = "idx_booking_uid", columnList = "bookingUid", unique = true)
        }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingUid;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public Booking() {
    }


    public Long getId() { return id; }
    public String getBookingUid() { return bookingUid; }
    public void setBookingUid(String bookingUid) { this.bookingUid = bookingUid; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

