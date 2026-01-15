package ru.mifi.booking.hotelservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import ru.mifi.booking.hotelservice.dto.ConfirmAvailabilityRequest;
import ru.mifi.booking.hotelservice.entity.Room;
import ru.mifi.booking.hotelservice.repository.RoomLockRepository;
import ru.mifi.booking.hotelservice.repository.RoomRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные проверки для критерия 7.1 (Hotel-service):
 * <ul>
 *     <li>конкурентный confirm-availability (5 параллельных запросов) — ровно один успех, остальные 409</li>
 *     <li>идемпотентность confirm-availability по requestId</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class RoomConfirmConcurrencyIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomLockRepository roomLockRepository;

    private Long roomId;

    @BeforeEach
    void setUp() {
        Room room = roomRepository.findAllAvailable().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Для тестов нужен хотя бы один доступный номер"));

        this.roomId = room.getId();

        // Чистим блокировки и статистику, чтобы тесты были независимыми.
        roomLockRepository.deleteAll();
        room.setTimesBooked(0);
        roomRepository.saveAndFlush(room);
    }

    @Test
    void concurrentConfirm_shouldAllowOnlyOneLock_andOthersGet409() throws Exception {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<CompletableFuture<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String requestId = "req-parallel-" + i + "-" + UUID.randomUUID();
            String bookingId = "booking-parallel-" + i;
            ConfirmAvailabilityRequest body = new ConfirmAvailabilityRequest(start, end, bookingId, requestId);

            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    ready.countDown();
                    startLatch.await();

                    return mockMvc.perform(
                                    post("/api/rooms/{id}/confirm-availability", roomId)
                                            .with(SecurityMockMvcRequestPostProcessors.user("svc").roles("SERVICE"))
                                            .contentType("application/json")
                                            .content(objectMapper.writeValueAsString(body))
                            )
                            .andReturn()
                            .getResponse()
                            .getStatus();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, pool));
        }

        // Дожидаемся, что все потоки готовы, и стартуем одновременно.
        ready.await();
        startLatch.countDown();

        List<Integer> statuses = futures.stream().map(CompletableFuture::join).toList();
        pool.shutdownNow();

        long ok = statuses.stream().filter(s -> s == 200).count();
        long conflict = statuses.stream().filter(s -> s == 409).count();

        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(4);

        // Доп. проверка: в БД должна остаться ровно 1 блокировка.
        assertThat(roomLockRepository.count()).isEqualTo(1);
    }

    @Test
    void confirmShouldBeIdempotentByRequestId() throws Exception {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(12);

        String requestId = "req-idempotent-" + UUID.randomUUID();
        String bookingId = "booking-idempotent-" + UUID.randomUUID();

        ConfirmAvailabilityRequest body = new ConfirmAvailabilityRequest(start, end, bookingId, requestId);

        // Первый вызов — создаёт блокировку и увеличивает timesBooked.
        mockMvc.perform(
                        post("/api/rooms/{id}/confirm-availability", roomId)
                                .with(SecurityMockMvcRequestPostProcessors.user("svc").roles("SERVICE"))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isOk());

        // Повтор с тем же requestId должен быть 200 OK и не менять статистику повторно.
        mockMvc.perform(
                        post("/api/rooms/{id}/confirm-availability", roomId)
                                .with(SecurityMockMvcRequestPostProcessors.user("svc").roles("SERVICE"))
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(body))
                )
                .andExpect(status().isOk());

        Room roomAfter = roomRepository.findById(roomId).orElseThrow();
        assertThat(roomAfter.getTimesBooked()).isEqualTo(1);
        assertThat(roomLockRepository.count()).isEqualTo(1);
    }
}
