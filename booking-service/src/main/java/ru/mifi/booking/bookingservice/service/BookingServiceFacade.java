package ru.mifi.booking.bookingservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.mifi.booking.bookingservice.client.HotelServiceClient;
import ru.mifi.booking.bookingservice.client.dto.ConfirmAvailabilityRequest;
import ru.mifi.booking.bookingservice.client.dto.HotelRoomDto;
import ru.mifi.booking.bookingservice.dto.BookingDtos;
import ru.mifi.booking.bookingservice.entity.Booking;
import ru.mifi.booking.bookingservice.entity.BookingStatus;
import ru.mifi.booking.bookingservice.repository.BookingRepository;
import ru.mifi.booking.bookingservice.security.JwtService;
import ru.mifi.booking.common.exception.ApiException;
import ru.mifi.booking.common.exception.BadRequestException;
import ru.mifi.booking.common.exception.ConflictException;
import ru.mifi.booking.common.exception.NotFoundException;
import ru.mifi.booking.common.exception.ServiceUnavailableException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BookingServiceFacade {

    private final BookingRepository bookingRepository;
    private final IdempotencyService idempotencyService;
    private final HotelServiceClient hotelServiceClient;
    private final JwtService jwtService;
    private final TransactionTemplate transactionTemplate;

    public BookingServiceFacade(
            BookingRepository bookingRepository,
            IdempotencyService idempotencyService,
            HotelServiceClient hotelServiceClient,
            JwtService jwtService,
            PlatformTransactionManager transactionManager
    ) {
        this.bookingRepository = bookingRepository;
        this.idempotencyService = idempotencyService;
        this.hotelServiceClient = hotelServiceClient;
        this.jwtService = jwtService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Page<BookingDtos.BookingResponse> listByUser(Long userId, Pageable pageable) {
        return bookingRepository.findByUserId(userId, pageable).map(this::toDto);
    }

    public BookingDtos.BookingResponse get(Long id, Long userId) {
        Booking b = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking " + id + " not found"));

        if (!b.getUserId().equals(userId)) {
            throw new NotFoundException("Booking " + id + " not found");
        }
        return toDto(b);
    }

    public BookingDtos.BookingResponse create(Long userId, BookingDtos.CreateBookingRequest req, String requestId) {
        validateDates(req.startDate(), req.endDate());

        idempotencyService.rememberOrThrow(requestId);

        String serviceJwt = jwtService.generateServiceToken();
        Long roomId = resolveRoomId(req, serviceJwt, requestId);

        Booking pending = createPendingBooking(userId, roomId, req.startDate(), req.endDate(), requestId);

        ConfirmAvailabilityRequest confirmReq = new ConfirmAvailabilityRequest(
                pending.getStartDate(),
                pending.getEndDate(),
                pending.getBookingUid(),
                requestId
        );

        try {
            hotelServiceClient.confirmAvailability(roomId, confirmReq, serviceJwt, requestId);

            updateStatus(pending.getId(), BookingStatus.CONFIRMED);
            return toDto(getBookingOrThrow(pending.getId()));

        } catch (ConflictException ex) {
            updateStatus(pending.getId(), BookingStatus.CANCELLED);
            safeRelease(roomId, pending.getBookingUid(), serviceJwt, requestId);
            throw ex;

        } catch (Exception ex) {
            updateStatus(pending.getId(), BookingStatus.CANCELLED);
            safeRelease(roomId, pending.getBookingUid(), serviceJwt, requestId);

            if (ex instanceof ApiException apiEx) {
                throw apiEx;
            }

            throw new ServiceUnavailableException("Hotel service call failed: " + ex.getMessage());
        }
    }

    public void cancel(Long id, Long userId) {
        Booking b = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking " + id + " not found"));

        if (!b.getUserId().equals(userId)) {
            throw new NotFoundException("Booking " + id + " not found");
        }

        if (b.getStatus() == BookingStatus.CANCELLED) {
            return;
        }

        String serviceJwt = jwtService.generateServiceToken();
        safeRelease(b.getRoomId(), b.getBookingUid(), serviceJwt, null);

        updateStatus(b.getId(), BookingStatus.CANCELLED);
    }

    private Booking createPendingBooking(Long userId,
                                         Long roomId,
                                         LocalDate startDate,
                                         LocalDate endDate,
                                         String requestId) {

        return transactionTemplate.execute(status -> {
            Booking booking = new Booking();
            booking.setUserId(userId);
            booking.setRoomId(roomId);
            booking.setStartDate(startDate);
            booking.setEndDate(endDate);
            booking.setStatus(BookingStatus.PENDING);
            booking.setCreatedAt(OffsetDateTime.now());
            booking.setBookingUid(UUID.randomUUID().toString());

            return bookingRepository.save(booking);
        });
    }

    private void updateStatus(Long bookingId, BookingStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            Booking b = getBookingOrThrow(bookingId);
            b.setStatus(status);
        });
    }

    private Booking getBookingOrThrow(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found"));
    }

    private Long resolveRoomId(BookingDtos.CreateBookingRequest req, String serviceJwt, String requestId) {
        if (!req.autoSelect()) {
            if (req.roomId() == null) {
                throw new BadRequestException("roomId is required when autoSelect=false");
            }
            return req.roomId();
        }

        List<HotelRoomDto> rooms = hotelServiceClient.recommendRooms(req.startDate(), req.endDate(), serviceJwt, requestId);
        if (rooms == null || rooms.isEmpty()) {
            throw new ConflictException("No available rooms for this period");
        }
        return rooms.getFirst().id();
    }

    private void safeRelease(Long roomId, String bookingUid, String serviceJwt, String requestId) {
        try {
            hotelServiceClient.release(roomId, bookingUid, serviceJwt, requestId);
        } catch (Exception ignored) {
        }
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (start.isAfter(end) || start.isEqual(end)) {
            throw new BadRequestException("Invalid dates: startDate must be before endDate");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new BadRequestException("Invalid dates: startDate must be today or later");
        }
    }

    private BookingDtos.BookingResponse toDto(Booking b) {
        return new BookingDtos.BookingResponse(
                b.getId(),
                b.getBookingUid(),
                b.getRoomId(),
                b.getStartDate(),
                b.getEndDate(),
                b.getStatus()
        );
    }
}
