package ru.mifi.booking.hotelservice.entity;

import jakarta.persistence.*;

/**
 * Сущность отеля.
 * В этом сервисе отель — справочник (имя + адрес).
 */
@Entity
@Table(name = "hotels")
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Название отеля. */
    @Column(nullable = false)
    private String name;

    /** Адрес отеля. */
    @Column(nullable = false)
    private String address;

    public Hotel() {}

    public Hotel(Long id, String name, String address) {
        this.id = id;
        this.name = name;
        this.address = address;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setAddress(String address) { this.address = address; }
}
