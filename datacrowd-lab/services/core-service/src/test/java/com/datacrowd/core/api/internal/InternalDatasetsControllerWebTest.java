package com.datacrowd.core.api.internal;

import com.datacrowd.core.api.ApiExceptionHandler;
import com.datacrowd.core.entity.DatasetStatus;
import com.datacrowd.core.service.DatasetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalDatasetsControllerWebTest {

    MockMvc mockMvc;
    DatasetService datasetService;

    @BeforeEach
    void setUp() {
        datasetService = mock(DatasetService.class);
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new InternalDatasetsController(datasetService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void updateStatus_returnsOk_andCallsService() throws Exception {
        UUID datasetId = UUID.randomUUID();

        mockMvc.perform(patch("/internal/datasets/{id}/status", datasetId)
                        .queryParam("status", "READY"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verify(datasetService).updateDatasetStatusInternal(datasetId, DatasetStatus.READY);
    }

    @Test
    void updateStatus_returns400_whenStatusInvalid() throws Exception {
        UUID datasetId = UUID.randomUUID();

        mockMvc.perform(patch("/internal/datasets/{id}/status", datasetId)
                        .queryParam("status", "NO_SUCH_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }
}
