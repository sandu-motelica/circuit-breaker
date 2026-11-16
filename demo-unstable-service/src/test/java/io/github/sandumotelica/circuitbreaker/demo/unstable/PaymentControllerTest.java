package io.github.sandumotelica.circuitbreaker.demo.unstable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "demo.unstable.failure-probability=1.0",
        "demo.unstable.slow-probability=0.0",
        "demo.unstable.slow-delay=1ms"
})
@AutoConfigureMockMvc
class PaymentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsPaymentForSuccessfulCall() throws Exception {
        mockMvc.perform(get("/api/payments/payment-123").param("mode", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value("payment-123"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void returnsServiceUnavailableForForcedAndRandomFailures() throws Exception {
        mockMvc.perform(get("/api/payments/payment-123").param("mode", "FAILURE"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/payments/payment-123"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void slowModeEventuallyReturnsPayment() throws Exception {
        mockMvc.perform(get("/api/payments/payment-123").param("mode", "SLOW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectsUnsupportedMode() throws Exception {
        mockMvc.perform(get("/api/payments/payment-123").param("mode", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }
}
