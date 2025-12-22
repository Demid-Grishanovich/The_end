package com.datacrowd.auth.api;

import com.datacrowd.auth.api.AuthDtos.AuthResponse;
import com.datacrowd.auth.api.AuthDtos.LoginRequest;
import com.datacrowd.auth.api.AuthDtos.RegisterRequest;
import com.datacrowd.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // убираем Spring Security фильтры, чтобы не было 403
class AuthControllerWebTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    @MockitoBean
    AuthService authService;

    @Test
    void register_returns200() throws Exception {
        var req = new RegisterRequest("demo", "demo@example.com", "pass");
        var resp = new AuthResponse("jwt-token", UUID.randomUUID().toString(), "WORKER");

        Mockito.when(authService.register(Mockito.any(RegisterRequest.class))).thenReturn(resp);

        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.role").value("WORKER"));
    }

    @Test
    void login_returns200() throws Exception {
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
