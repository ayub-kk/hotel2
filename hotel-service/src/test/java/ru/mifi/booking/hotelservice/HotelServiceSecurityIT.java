package ru.mifi.booking.hotelservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные проверки критерия 7.3 (Security tests):
 * <ul>
 *     <li>без токена — 401</li>
 *     <li>USER не может POST /api/hotels — 403</li>
 *     <li>USER не может дергать internal endpoints — 403</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
class HotelServiceSecurityIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotCreateHotel_shouldReturn403() throws Exception {
        mockMvc.perform(
                        post("/api/hotels")
                                .with(SecurityMockMvcRequestPostProcessors.user("2").roles("USER"))
                                .contentType("application/json")
                                .content("{\"name\":\"Test Hotel\",\"address\":\"Test City\"}")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotCallInternalConfirmAvailability_shouldReturn403() throws Exception {
        mockMvc.perform(
                        post("/api/rooms/{id}/confirm-availability", 1)
                                .with(SecurityMockMvcRequestPostProcessors.user("2").roles("USER"))
                                .contentType("application/json")
                                .content("{\"startDate\":\"2030-01-01\",\"endDate\":\"2030-01-02\",\"bookingId\":\"b\",\"requestId\":\"r\"}")
                )
                .andExpect(status().isForbidden());
    }
}
