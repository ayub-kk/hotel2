package ru.mifi.booking.hotelservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hotel Service.
 *
 * <p>
 * Этот микросервис будет отвечать за отели, номера и доступность.
 * На текущем шаге я делаю минимальный каркас и подключаю регистрацию в Eureka.
 * </p>
 */
@SpringBootApplication
public class HotelServiceApplication {

    /**
     * Точка входа Spring Boot приложения.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(HotelServiceApplication.class, args);
    }
}
