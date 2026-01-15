package ru.mifi.booking.bookingservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.RestTemplate;

import ru.mifi.booking.bookingservice.dto.BookingDtos;
import ru.mifi.booking.bookingservice.entity.Booking;
import ru.mifi.booking.bookingservice.repository.BookingRepository;
// ✅ если есть таблица идемпотентности:
import ru.mifi.booking.bookingservice.repository.RequestLogRepository;

import ru.mifi.booking.common.dto.ErrorDto;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BookingSagaIT.TestRestTemplateConfig.class)
class BookingSagaIT {

    private static MockWebServer mockWebServer;
    private static String baseUrl;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookingRepository bookingRepository;

    // ✅ если у вас есть RequestLogRepository — обязательно чистим для изоляции тестов
    @Autowired(required = false)
    private RequestLogRepository requestLogRepository;

    private static synchronized String getBaseUrl() {
        if (mockWebServer == null) {
            mockWebServer = new MockWebServer();
            try {
                mockWebServer.start();
            } catch (IOException e) {
                throw new RuntimeException("Failed to start MockWebServer", e);
            }
            baseUrl = "http://localhost:" + mockWebServer.getPort();
        }
        return baseUrl;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("services.hotel-service.base-url", BookingSagaIT::getBaseUrl);

        // быстро и детерминированно
        registry.add("services.hotel-service.retry.max-attempts", () -> "3");
        registry.add("services.hotel-service.retry.backoff-ms", () -> "10,10,10");
        registry.add("services.hotel-service.connect-timeout-ms", () -> "50");
        registry.add("services.hotel-service.read-timeout-ms", () -> "50");

        // в тестах discovery не нужен
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    @AfterAll
    static void shutdown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @BeforeEach
    void reset() throws Exception {
        getBaseUrl();

        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setResponseCode(404);
            }
        });

        drainRequests();
        bookingRepository.deleteAll();
        if (requestLogRepository != null) {
            requestLogRepository.deleteAll();
        }
    }

    @Test
    void successfulFlow_confirmsAndPersistsBooking() throws Exception {
        String requestId = "rq-success-1";

        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path != null && path.startsWith("/api/rooms/recommend")) {
                    // базовые проверки корреляции
                    assertThat(request.getHeader("X-Request-Id")).isEqualTo(requestId);
                    assertThat(request.getHeader("Authorization")).startsWith("Bearer ");

                    return json(200, "[{\"id\":1,\"hotelId\":77,\"number\":\"101\",\"available\":true}]");
                }

                if ("/api/rooms/1/confirm-availability".equals(path)) {
                    assertThat(request.getHeader("X-Request-Id")).isEqualTo(requestId);
                    assertThat(request.getHeader("Authorization")).startsWith("Bearer ");
                    return new MockResponse().setResponseCode(200);
                }

                return new MockResponse().setResponseCode(404);
            }
        });

        BookingDtos.CreateBookingRequest req = createRequest(true, null);

        MvcResult mvcResult = mockMvc.perform(
                        post("/api/booking")
                                .with(userJwt(2))
                                .header("X-Request-Id", requestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isOk())
                .andReturn();

        BookingDtos.BookingResponse response =
                objectMapper.readValue(mvcResult.getResponse().getContentAsString(), BookingDtos.BookingResponse.class);

        assertThat(response).isNotNull();
        assertThat(String.valueOf(response.status())).isEqualTo("CONFIRMED");
        assertThat(response.roomId()).isEqualTo(1L);

        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).hasSize(1);
        assertThat(bookings.getFirst().getRoomId()).isEqualTo(1L);
        assertThat(bookings.getFirst().getStatus().name()).isEqualTo("CONFIRMED");

        List<RecordedRequest> requests = takeAllRequests(400);
        assertThat(countPathStartsWith(requests, "/api/rooms/recommend")).isEqualTo(1);
        assertThat(countPathEquals(requests, "/api/rooms/1/confirm-availability")).isEqualTo(1);
        assertThat(countPathStartsWith(requests, "/api/rooms/1/release")).isEqualTo(0);
    }

    @Test
    void roomNotAvailable_conflict_409_cancelsAndReleasesRoom_noRetry() throws Exception {
        String requestId = "rq-conflict-1";

        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path != null && path.startsWith("/api/rooms/recommend")) {
                    return json(200, "[{\"id\":1,\"hotelId\":77,\"number\":\"101\",\"available\":true}]");
                }

                if ("/api/rooms/1/confirm-availability".equals(path)) {
                    // конфликт НЕ должен ретраиться
                    return new MockResponse().setResponseCode(409);
                }

                if (path != null && path.startsWith("/api/rooms/1/release")) {
                    return new MockResponse().setResponseCode(200);
                }

                return new MockResponse().setResponseCode(404);
            }
        });

        BookingDtos.CreateBookingRequest req = createRequest(true, null);

        MvcResult mvcResult = mockMvc.perform(
                        post("/api/booking")
                                .with(userJwt(2))
                                .header("X-Request-Id", requestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isConflict())
                .andReturn();

        ErrorDto err = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ErrorDto.class);
        assertThat(err.getStatus()).isEqualTo(409);
        assertThat(err.getPath()).isEqualTo("/api/booking");
        assertThat(err.getRequestId()).isEqualTo(requestId);

        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).hasSize(1);
        assertThat(bookings.getFirst().getStatus().name()).isEqualTo("CANCELLED");

        List<RecordedRequest> requests = takeAllRequests(500);
        assertThat(countPathStartsWith(requests, "/api/rooms/recommend")).isEqualTo(1);
        assertThat(countPathEquals(requests, "/api/rooms/1/confirm-availability")).isEqualTo(1);
        assertThat(countPathStartsWith(requests, "/api/rooms/1/release")).isEqualTo(1);
    }

    @Test
    void availabilityTimeout_retriesThen503_andReleasesRoom() throws Exception {
        String requestId = "rq-timeout-1";

        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if (path != null && path.startsWith("/api/rooms/recommend")) {
                    return json(200, "[{\"id\":1,\"hotelId\":77,\"number\":\"101\",\"available\":true}]");
                }

                if ("/api/rooms/1/confirm-availability".equals(path)) {
                    return new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE);
                }

                if (path != null && path.startsWith("/api/rooms/1/release")) {
                    return new MockResponse().setResponseCode(200);
                }

                return new MockResponse().setResponseCode(404);
            }
        });

        BookingDtos.CreateBookingRequest req = createRequest(true, null);

        MvcResult mvcResult = mockMvc.perform(
                        post("/api/booking")
                                .with(userJwt(2))
                                .header("X-Request-Id", requestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isServiceUnavailable())
                .andReturn();

        ErrorDto err = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ErrorDto.class);
        assertThat(err.getStatus()).isEqualTo(503);
        assertThat(err.getRequestId()).isEqualTo(requestId);

        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).hasSize(1);
        assertThat(bookings.getFirst().getStatus().name()).isEqualTo("CANCELLED");

        List<RecordedRequest> requests = takeAllRequests(1200);
        assertThat(countPathEquals(requests, "/api/rooms/1/confirm-availability")).isEqualTo(3);
        assertThat(countPathStartsWith(requests, "/api/rooms/1/release")).isEqualTo(1);
    }

    @Test
    void idempotency_sameRequestIdSecondCallReturns409_andDoesNotCallHotelServiceTwice() throws Exception {
        String requestId = "rq-idem-123";

        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/api/rooms/recommend")) {
                    return json(200, "[{\"id\":1,\"hotelId\":77,\"number\":\"101\",\"available\":true}]");
                }
                if ("/api/rooms/1/confirm-availability".equals(path)) {
                    return new MockResponse().setResponseCode(200);
                }
                return new MockResponse().setResponseCode(404);
            }
        });

        BookingDtos.CreateBookingRequest req = createRequest(true, null);

        // 1) первый вызов — 200
        mockMvc.perform(
                        post("/api/booking")
                                .with(userJwt(2))
                                .header("X-Request-Id", requestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isOk());

        List<RecordedRequest> first = takeAllRequests(600);
        assertThat(countPathStartsWith(first, "/api/rooms/recommend")).isEqualTo(1);
        assertThat(countPathEquals(first, "/api/rooms/1/confirm-availability")).isEqualTo(1);

        // 2) второй вызов с тем же X-Request-Id — 409 и НОЛЬ вызовов hotel-service
        mockMvc.perform(
                        post("/api/booking")
                                .with(userJwt(2))
                                .header("X-Request-Id", requestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req))
                )
                .andExpect(status().isConflict());

        List<RecordedRequest> second = takeAllRequests(400);
        assertThat(second).isEmpty();

        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentBookings_sameRoom_oneOk_one409() throws Exception {
        // Тут намеренно manual select, чтобы убрать recommend и сделать тест максимально “чистым”.
        BookingDtos.CreateBookingRequest req = createRequest(false, 1L);

        AtomicInteger confirmCounter = new AtomicInteger(0);

        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();

                if ("/api/rooms/1/confirm-availability".equals(path)) {
                    int n = confirmCounter.incrementAndGet();
                    return (n == 1)
                            ? new MockResponse().setResponseCode(200)
                            : new MockResponse().setResponseCode(409);
                }

                if (path != null && path.startsWith("/api/rooms/1/release")) {
                    return new MockResponse().setResponseCode(200);
                }

                return new MockResponse().setResponseCode(404);
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> callA = () -> {
            start.await();
            return mockMvc.perform(
                    post("/api/booking")
                            .with(userJwt(2))
                            .header("X-Request-Id", "rq-par-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
            ).andReturn().getResponse().getStatus();
        };

        Callable<Integer> callB = () -> {
            start.await();
            return mockMvc.perform(
                    post("/api/booking")
                            .with(userJwt(2))
                            .header("X-Request-Id", "rq-par-2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
            ).andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = pool.submit(callA);
        Future<Integer> f2 = pool.submit(callB);

        start.countDown();

        int s1 = f1.get(5, TimeUnit.SECONDS);
        int s2 = f2.get(5, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 409);

        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).hasSize(2);

        long confirmed = bookings.stream().filter(b -> "CONFIRMED".equals(b.getStatus().name())).count();
        long cancelled = bookings.stream().filter(b -> "CANCELLED".equals(b.getStatus().name())).count();
        assertThat(confirmed).isEqualTo(1);
        assertThat(cancelled).isEqualTo(1);

        List<RecordedRequest> requests = takeAllRequests(800);
        assertThat(countPathEquals(requests, "/api/rooms/1/confirm-availability")).isEqualTo(2);
        assertThat(countPathStartsWith(requests, "/api/rooms/1/release")).isEqualTo(1);
    }

    // ---------------- helpers ----------------

    private static MockResponse json(int code, String json) {
        return new MockResponse()
                .setResponseCode(code)
                .addHeader("Content-Type", "application/json")
                .setBody(json);
    }

    private void drainRequests() throws InterruptedException {
        while (mockWebServer.takeRequest(10, TimeUnit.MILLISECONDS) != null) {
            // no-op
        }
    }

    private List<RecordedRequest> takeAllRequests(long waitMs) throws InterruptedException {
        List<RecordedRequest> requests = new ArrayList<>();
        RecordedRequest r;
        long deadline = System.currentTimeMillis() + waitMs;

        while (System.currentTimeMillis() < deadline) {
            r = mockWebServer.takeRequest(100, TimeUnit.MILLISECONDS);
            if (r == null) break;
            requests.add(r);
        }
        return requests;
    }

    private static int countPathEquals(List<RecordedRequest> requests, String exactPath) {
        int count = 0;
        for (RecordedRequest r : requests) {
            if (exactPath.equals(r.getPath())) count++;
        }
        return count;
    }

    private static int countPathStartsWith(List<RecordedRequest> requests, String prefix) {
        int count = 0;
        for (RecordedRequest r : requests) {
            String path = r.getPath();
            if (path != null && path.startsWith(prefix)) count++;
        }
        return count;
    }

    private RequestPostProcessor userJwt(long userId) {
        return jwt()
                .jwt(token -> token.subject(String.valueOf(userId)).claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private BookingDtos.CreateBookingRequest createRequest(boolean autoSelect, Long roomId) {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = start.plusDays(5);
        return new BookingDtos.CreateBookingRequest(start, end, autoSelect, roomId);
    }

    /**
     * В тестах важно НЕ потерять таймауты, иначе NO_RESPONSE может “повесить” прогон.
     * Этот RestTemplate гарантирует connect/read timeout из test properties.
     */
    @TestConfiguration
    static class TestRestTemplateConfig {

        @Bean
        @Primary
        public RestTemplate testRestTemplate(
                @Value("${services.hotel-service.connect-timeout-ms}") int connectTimeoutMs,
                @Value("${services.hotel-service.read-timeout-ms}") int readTimeoutMs
        ) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(connectTimeoutMs);
            factory.setReadTimeout(readTimeoutMs);
            return new RestTemplate(factory);
        }
    }
}
