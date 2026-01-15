package ru.mifi.booking.bookingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mifi.booking.bookingservice.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
