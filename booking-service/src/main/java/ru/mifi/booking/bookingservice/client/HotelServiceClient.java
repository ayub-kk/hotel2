package ru.mifi.booking.bookingservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.mifi.booking.bookingservice.client.dto.ConfirmAvailabilityRequest;
import ru.mifi.booking.bookingservice.client.dto.HotelRoomDto;
import ru.mifi.booking.common.dto.ErrorDto;
import ru.mifi.booking.common.exception.ConflictException;
import ru.mifi.booking.common.exception.BadRequestException;
import ru.mifi.booking.common.exception.NotFoundException;
import ru.mifi.booking.common.exception.ServiceUnavailableException;
import ru.mifi.booking.common.exception.UnauthorizedException;
import ru.mifi.booking.common.http.RequestHeaders;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@Service
public class HotelServiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int maxAttempts;
    private final long[] backoffMs;

    public HotelServiceClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${services.hotel-service.base-url}") String baseUrl,
            @Value("${services.hotel-service.retry.max-attempts:3}") int maxAttempts,
            @Value("${services.hotel-service.retry.backoff-ms:200,500,1000}") String backoffMsCsv
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffMs = parseBackoff(backoffMsCsv);
    }

    public List<HotelRoomDto> recommendRooms(LocalDate start, LocalDate end, String serviceJwt, String requestId) {
        String url = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/api/rooms/recommend")
                .queryParam("start", start)
                .queryParam("end", end)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceJwt);
        if (requestId != null && !requestId.isBlank()) {
            headers.set(RequestHeaders.X_REQUEST_ID, requestId);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        return executeWithRetry(() -> {
            try {
                ResponseEntity<HotelRoomDto[]> resp = restTemplate.exchange(url, HttpMethod.GET, entity, HotelRoomDto[].class);
                HotelRoomDto[] body = resp.getBody();
                return body == null ? List.of() : Arrays.asList(body);
            } catch (HttpClientErrorException ex) {
                mapAndThrow(ex);
                return List.of();
            }
        }, "recommend");
    }

    public void confirmAvailability(Long roomId, ConfirmAvailabilityRequest req, String serviceJwt, String requestId) {
        String url = baseUrl + "/api/rooms/" + roomId + "/confirm-availability";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(serviceJwt);
        if (requestId != null && !requestId.isBlank()) {
            headers.set(RequestHeaders.X_REQUEST_ID, requestId);
        }

        HttpEntity<ConfirmAvailabilityRequest> entity = new HttpEntity<>(req, headers);

        executeWithRetry(() -> {
            try {
                restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
                return null;
            } catch (HttpClientErrorException ex) {
                mapAndThrow(ex);
                return null;
            }
        }, "confirm-availability");
    }

    public void release(Long roomId, String bookingId, String serviceJwt, String requestId) {
        String url = baseUrl + "/api/rooms/" + roomId + "/release?bookingId=" + bookingId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceJwt);
        if (requestId != null && !requestId.isBlank()) {
            headers.set(RequestHeaders.X_REQUEST_ID, requestId);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        executeWithRetry(() -> {
            try {
                restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
                return null;
            } catch (HttpClientErrorException ex) {
                mapAndThrow(ex);
                return null;
            }
        }, "release");
    }

    private <T> T executeWithRetry(Supplier<T> action, String operationName) {
        int attempt = 0;

        while (true) {
            try {
                attempt++;
                return action.get();

            } catch (RestClientException ex) {
                if (attempt >= maxAttempts) {
                    throw new ServiceUnavailableException(
                            "Hotel service is unavailable during '" + operationName + "' after " + attempt + " attempt(s)"
                    );
                }
                sleepBackoff(attempt);

            } catch (ServiceUnavailableException ex) {
                // внутренний фейл (например, бросили мы сами) — тоже ретраим
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                sleepBackoff(attempt);
            }
        }
    }

    private void sleepBackoff(int attempt) {
        long delay = 0L;
        if (backoffMs.length > 0) {
            int idx = Math.min(Math.max(0, attempt - 1), backoffMs.length - 1);
            delay = backoffMs[idx];
        }

        if (delay <= 0) {
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private long[] parseBackoff(String csv) {
        if (csv == null || csv.isBlank()) {
            return new long[]{200, 500, 1000};
        }
        try {
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .mapToLong(Long::parseLong)
                    .toArray();
        } catch (Exception ex) {
            return new long[]{200, 500, 1000};
        }
    }

    private void mapAndThrow(HttpClientErrorException ex) {
        HttpStatusCode code = ex.getStatusCode();

        String message = extractMessage(ex);

        if (code.value() == 400) {
            throw new BadRequestException(message == null || message.isBlank()
                    ? "Bad request to hotel service"
                    : message);
        }

        if (code.value() == 401 || code.value() == 403) {
            throw new UnauthorizedException("Hotel service rejected service token");
        }
        if (code.value() == 404) {
            throw new NotFoundException("Hotel service resource not found");
        }
        if (code.value() == 409) {
            throw new ConflictException(message == null || message.isBlank()
                    ? "Hotel service conflict"
                    : message);
        }

        throw ex;
    }

    private String extractMessage(HttpClientErrorException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return null;
        }

        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            return body;
        }

        try {
            ErrorDto dto = objectMapper.readValue(trimmed, ErrorDto.class);
            if (dto != null) {
                if (dto.getMessage() != null && !dto.getMessage().isBlank()) {
                    return dto.getMessage();
                }
                if (dto.getError() != null && !dto.getError().isBlank()) {
                    return dto.getError();
                }
            }
        } catch (Exception ignored) {
        }
        return body;
    }
}
