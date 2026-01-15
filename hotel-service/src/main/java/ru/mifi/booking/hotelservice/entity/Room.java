package ru.mifi.booking.hotelservice.entity;

import jakarta.persistence.*;

/**
 * Сущность номера.
 * timesBooked — метрика "справедливости" для /rooms/recommend (меньше бронирований — выше приоритет).
 */
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Отель-владелец номера. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    /** Номер/идентификатор комнаты (например, "101"). */
    @Column(nullable = false)
    private String number;

    /** Флаг доступности номера в целом (если номер "выведен из эксплуатации"). */
    @Column(nullable = false)
    private boolean available = true;

    /** Сколько раз номер был забронирован (для рекомендаций). */
    @Column(name = "times_booked", nullable = false)
    private long timesBooked = 0;

    public Room() {}

    public Room(Long id, Hotel hotel, String number, boolean available, long timesBooked) {
        this.id = id;
        this.hotel = hotel;
        this.number = number;
        this.available = available;
        this.timesBooked = timesBooked;
    }

    public Long getId() { return id; }
    public Hotel getHotel() { return hotel; }
    public String getNumber() { return number; }
    public boolean isAvailable() { return available; }
    public long getTimesBooked() { return timesBooked; }

    public void setId(Long id) { this.id = id; }
    public void setHotel(Hotel hotel) { this.hotel = hotel; }
    public void setNumber(String number) { this.number = number; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setTimesBooked(long timesBooked) { this.timesBooked = timesBooked; }
}

