package ru.mifi.booking.bookingservice.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mifi.booking.bookingservice.dto.CreateUserRequest;
import ru.mifi.booking.bookingservice.dto.UpdateUserRequest;
import ru.mifi.booking.bookingservice.dto.UserDto;
import ru.mifi.booking.bookingservice.entity.User;
import ru.mifi.booking.bookingservice.entity.UserRole;
import ru.mifi.booking.bookingservice.repository.UserRepository;
import ru.mifi.booking.common.exception.BadRequestException;
import ru.mifi.booking.common.exception.ConflictException;
import ru.mifi.booking.common.exception.NotFoundException;

@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserDto create(CreateUserRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered");
        }

        UserRole role = parseRole(req.role());

        User user = new User(null, req.name(), req.email(), passwordEncoder.encode(req.password()), role);
        User saved = userRepository.save(user);

        return toDto(saved);
    }

    @Transactional
    public UserDto update(UpdateUserRequest req) {
        User user = userRepository.findById(req.id())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (req.email() != null && !req.email().isBlank() && !req.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(req.email())) {
                throw new ConflictException("Email already registered");
            }
            user.setEmail(req.email());
        }

        if (req.name() != null && !req.name().isBlank()) {
            user.setName(req.name());
        }

        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }

        if (req.role() != null && !req.role().isBlank()) {
            user.setRole(parseRole(req.role()));
        }

        return toDto(user);
    }

    @Transactional
    public void delete(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("Invalid user id");
        }
        if (!userRepository.existsById(id)) {
            throw new NotFoundException("User not found");
        }
        userRepository.deleteById(id);
    }

    private UserRole parseRole(String raw) {
        try {
            return UserRole.valueOf(raw.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid role. Allowed: USER, ADMIN");
        }
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }
}
