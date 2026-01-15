package ru.mifi.booking.bookingservice.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mifi.booking.bookingservice.dto.*;
import ru.mifi.booking.bookingservice.service.AuthService;
import ru.mifi.booking.bookingservice.service.UserAdminService;

@RestController
@RequestMapping("/api/user")
public class AuthController {

    private final AuthService authService;
    private final UserAdminService userAdminService;

    public AuthController(AuthService authService, UserAdminService userAdminService) {
        this.authService = authService;
        this.userAdminService = userAdminService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/auth")
    public AuthResponse auth(@Valid @RequestBody AuthRequest req) {
        return authService.auth(req);
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public UserDto createUser(@Valid @RequestBody CreateUserRequest req) {
        return userAdminService.create(req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping
    public UserDto updateUser(@Valid @RequestBody UpdateUserRequest req) {
        return userAdminService.update(req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping
    public void deleteUser(@RequestParam("id") Long id) {
        userAdminService.delete(id);
    }
}
