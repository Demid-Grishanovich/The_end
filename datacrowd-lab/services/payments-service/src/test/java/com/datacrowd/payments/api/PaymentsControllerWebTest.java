package com.datacrowd.payments.api;

import com.datacrowd.payments.security.JwtService;
import com.datacrowd.payments.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentsController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentsControllerWebTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PaymentService paymentService;

    // ИСПРАВЛЕНО: JwtAuthenticationFilter требует JwtService — мокируем
    @MockitoBean
    JwtService jwtService;

    @Test
    void ping_returnsOk() throws Exception {
        mvc.perform(get("/payments/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void success_returnsSuccessStatus() throws Exception {
        mvc.perform(get("/payments/checkout/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void cancel_returnsCancelStatus() throws Exception {
        mvc.perform(get("/payments/checkout/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancel"));
    }
}