package ru.mifi.booking.hotelservice.entity;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Временная блокировка номера под бронирование (часть саги).
 * bookingId — корреляция между сервисами.
 * requestId — идемпотентность (повторный confirm с тем же requestId не должен создавать дубль).
 */
@Entity
@Table(
        name = "room_locks",
        indexes = {
                @Index(name = "idx_room_lock_room", columnList = "room_id"),
                @Index(name = "idx_room_lock_booking", columnList = "booking_id", unique = true),
                @Index(name = "idx_room_lock_request", columnList = "request_id", unique = true)
        }
)
public class RoomLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Заблокированный номер. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "booking_id", nullable = false, unique = true)
    private String bookingId;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    public RoomLock() {}

    public RoomLock(Long id, Room room, LocalDate startDate, LocalDate endDate, String bookingId, String requestId) {
        this.id = id;
        this.room = room;
        this.startDate = startDate;
        this.endDate = endDate;
        this.bookingId = bookingId;
        this.requestId = requestId;
    }

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getBookingId() { return bookingId; }
    public String getRequestId() { return requestId; }

    public void setId(Long id) { this.id = id; }
    public void setRoom(Room room) { this.room = room; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}

