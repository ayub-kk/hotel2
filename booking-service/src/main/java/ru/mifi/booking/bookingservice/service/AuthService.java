package ru.mifi.booking.bookingservice.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.mifi.booking.bookingservice.dto.AuthRequest;
import ru.mifi.booking.bookingservice.dto.AuthResponse;
import ru.mifi.booking.bookingservice.dto.RegisterRequest;
import ru.mifi.booking.bookingservice.entity.User;
import ru.mifi.booking.bookingservice.entity.UserRole;
import ru.mifi.booking.bookingservice.repository.UserRepository;
import ru.mifi.booking.bookingservice.security.JwtService;
import ru.mifi.booking.common.exception.ConflictException;
import ru.mifi.booking.common.exception.UnauthorizedException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered");
        }

        User user = new User(
                null,
                req.name(),
                req.email(),
                passwordEncoder.encode(req.password()),
                UserRole.USER
        );

        User saved = userRepository.save(user);

        String token = jwtService.generateToken(saved.getId(), saved.getRole().name());
        return new AuthResponse(saved.getId(), token, saved.getRole().name());
    }

    public AuthResponse auth(AuthRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        String token = jwtService.generateToken(user.getId(), user.getRole().name());
        return new AuthResponse(user.getId(), token, user.getRole().name());
    }
}
