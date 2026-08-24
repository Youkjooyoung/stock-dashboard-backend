package com.stock.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigAuthorizationTest.TestEndpoints.class)
@Import({
        SecurityConfig.class,
        JwtAuthFilter.class,
        SecurityConfigAuthorizationTest.TestEndpoints.class
})
class SecurityConfigAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void latestPricesRemainPublic() throws Exception {
        mockMvc.perform(get("/api/stock/prices"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousUserCannotReadOperationalMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCannotStartCollection() throws Exception {
        mockMvc.perform(post("/api/stock/prices/005930/collect"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/stock/collect/range"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotStartCollection() throws Exception {
        mockMvc.perform(post("/api/stock/prices/005930/collect"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanStartCollection() throws Exception {
        mockMvc.perform(post("/api/stock/prices/005930/collect"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/stock/collect/range"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestEndpoints {

        @GetMapping("/api/stock/prices")
        void prices() {
        }

        @GetMapping("/actuator/health")
        void health() {
        }

        @GetMapping("/actuator/prometheus")
        void prometheus() {
        }

        @PostMapping("/api/stock/prices/{ticker}/collect")
        void collectTicker() {
        }

        @PostMapping("/api/stock/collect/range")
        void collectRange() {
        }
    }
}
