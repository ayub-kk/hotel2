package ru.mifi.booking.bookingservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import ru.mifi.booking.bookingservice.dto.BookingDtos;
import ru.mifi.booking.common.exception.UnauthorizedException;
import ru.mifi.booking.bookingservice.service.BookingServiceFacade;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingServiceFacade bookingService;

    public BookingController(BookingServiceFacade bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/booking")
    public BookingDtos.BookingResponse create(@Valid @RequestBody BookingDtos.CreateBookingRequest req,
                                              Authentication auth,
                                              @RequestHeader(name = "X-Request-Id", required = false) String requestId) {

        if (auth == null) {
            throw new UnauthorizedException("No auth");
        }

        Long userId = Long.parseLong(auth.getName());

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        return bookingService.create(userId, req, requestId);
    }

    @GetMapping("/bookings")
    public Page<BookingDtos.BookingResponse> list(
            Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        if (auth == null) throw new UnauthorizedException("No auth");
        Long userId = Long.parseLong(auth.getName());
        return bookingService.listByUser(userId, pageable);
    }

    @GetMapping("/booking/{id}")
    public BookingDtos.BookingResponse get(@PathVariable Long id, Authentication auth) {
        if (auth == null) throw new UnauthorizedException("No auth");
        Long userId = Long.parseLong(auth.getName());
        return bookingService.get(id, userId);
    }

    @DeleteMapping("/booking/{id}")
    public void cancel(@PathVariable Long id,
                       Authentication auth,
                       @RequestHeader(name = "X-Request-Id", required = false) String requestId) {

        if (auth == null) throw new UnauthorizedException("No auth");

        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();

        Long userId = Long.parseLong(auth.getName());
        bookingService.cancel(id, userId);
    }
}
