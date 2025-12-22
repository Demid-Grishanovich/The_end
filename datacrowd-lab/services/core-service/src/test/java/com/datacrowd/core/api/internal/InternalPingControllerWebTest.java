package com.datacrowd.core.api.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalPingControllerWebTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new InternalPingController())
                .build();
    }

    @Test
    void ping_returnsOk() throws Exception {
        mockMvc.perform(get("/internal/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
