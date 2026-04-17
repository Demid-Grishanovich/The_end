package com.datacrowd.auth.api;

import com.datacrowd.auth.api.AuthDtos.AuthResponse;
import com.datacrowd.auth.api.AuthDtos.LoginRequest;
import com.datacrowd.auth.api.AuthDtos.RegisterRequest;
import com.datacrowd.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    /**
     * ВАЖНО: @WebMvcTest часто поднимает дефолтную security (и даёт 403 на POST).
     * Поэтому здесь мы явно включаем тестовый SecurityFilterChain: permitAll + csrf disabled.
     */
    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(reg -> reg.anyRequest().permitAll())
                    .build();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockitoBean
    AuthService authService;


    @Test
    void login_returnsToken() throws Exception {
        var req = new LoginRequest("demo@example.com", "pass");
        var resp = new AuthResponse("jwt-token", UUID.randomUUID().toString(), "WORKER");

        Mockito.when(authService.login(Mockito.any(LoginRequest.class))).thenReturn(resp);

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.role").value("WORKER"));
    }
}
