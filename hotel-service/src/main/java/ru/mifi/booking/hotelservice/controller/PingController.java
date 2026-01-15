package ru.mifi.booking.hotelservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Контроллер для быстрой проверки доступности hotel-service.
 */
@RestController
public class PingController {

    /**
     * Проверка доступности.
     *
     * @return строка-ответ от hotel-service
     */
    @GetMapping("/ping")
    public String ping() {
        return "hotel-service: OK";
    }
}
