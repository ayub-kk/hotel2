package ru.mifi.booking.bookingservice.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mifi.booking.bookingservice.entity.User;
import ru.mifi.booking.bookingservice.entity.UserRole;
import ru.mifi.booking.bookingservice.repository.UserRepository;

@Component
public class BookingDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BookingDataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BookingDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean createdAny = false;

        createdAny |= ensureUser(
                "Admin",
                "admin@local",
                "admin123",
                UserRole.ADMIN
        );

        createdAny |= ensureUser(
                "Ivan User",
                "user@local",
                "user123",
                UserRole.USER
        );

        if (createdAny) {
            log.info("Initial users are ready: admin@local/admin123 (ADMIN), user@local/user123 (USER)");
        } else {
            log.info("Initial users already exist. Skipping seeding.");
        }
    }
    private boolean ensureUser(String name, String email, String password, UserRole role) {
        if (userRepository.existsByEmail(email)) {
            return false;
        }

        User user = new User(
                null,
                name,
                email,
                passwordEncoder.encode(password),
                role
        );

        userRepository.save(user);
        log.info("Seeded user: {} ({})", email, role);
        return true;
    }
}
