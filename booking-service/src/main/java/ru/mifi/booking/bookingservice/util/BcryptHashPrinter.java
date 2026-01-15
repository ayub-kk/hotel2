package ru.mifi.booking.bookingservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptHashPrinter {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode("admin123"));
    }
}
