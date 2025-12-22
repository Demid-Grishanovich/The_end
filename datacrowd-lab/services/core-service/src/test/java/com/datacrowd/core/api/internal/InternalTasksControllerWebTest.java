package com.datacrowd.core.api.internal;

import com.datacrowd.core.api.ApiExceptionHandler;
import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.entity.TaskBatchEntity;
import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.entity.TaskStatus;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.TaskBatchRepository;
import com.datacrowd.core.repo.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalTasksControllerWebTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    TaskRepository taskRepository;
    TaskBatchRepository taskBatchRepository;
    DatasetRepository datasetRepository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        taskRepository = mock(TaskRepository.class);
        taskBatchRepository = mock(TaskBatchRepository.class);
        datasetRepository = mock(DatasetRepository.class);

        InternalTasksController controller = new InternalTasksController(taskRepository, taskBatchRepository, datasetRepository);

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(
                        new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    void bulkCreate_returns400_whenTasksEmpty_andDoesNotSave() throws Exception {
        String body = "{\"tasks\": []}"; // @NotEmpty -> 400

        mockMvc.perform(post("/internal/tasks/bulk")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskRepository, taskBatchRepository, datasetRepository);
    }

    @Test
    void bulkCreate_savesOnlyTasksWithExistingBatchAndDataset_andParsesStatus() throws Exception {
        UUID batch1 = UUID.randomUUID();
        UUID batchMissing = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TaskBatchEntity b1 = new TaskBatchEntity();
        b1.setId(batch1);
        b1.setDatasetId(datasetId);

        when(taskBatchRepository.findAllById(anyIterable())).thenReturn(List.of(b1));

        DatasetEntity d = new DatasetEntity();
        d.setId(datasetId);
        d.setProjectId(projectId);

        when(datasetRepository.findAllById(anyIterable())).thenReturn(List.of(d));

        String body = """
                {
                  "tasks": [
                    {"batchId": "%s", "payloadJson": "{\\"x\\":1}", "status": "OPEN"},
                    {"batchId": "%s", "payloadJson": "{}", "status": "READY"}
                  ]
                }
                """.formatted(batch1, batchMissing);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaskEntity>> captor = ArgumentCaptor.forClass(List.class);
        when(taskRepository.saveAll(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/internal/tasks/bulk")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        List<TaskEntity> saved = captor.getValue();
        assertThat(saved).hasSize(1);

        TaskEntity t = saved.get(0);
        assertThat(t.getBatchId()).isEqualTo(batch1);
        assertThat(t.getDatasetId()).isEqualTo(datasetId);
        assertThat(t.getProjectId()).isEqualTo(projectId);
        assertThat(t.getPayloadJson()).isEqualTo("{\"x\":1}");
        assertThat(t.getTitle()).startsWith("Generated task #");
        assertThat(t.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    @Test
    void bulkCreate_invalidStatusFallsBackToNew() throws Exception {
        UUID batch1 = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TaskBatchEntity b1 = new TaskBatchEntity();
        b1.setId(batch1);
        b1.setDatasetId(datasetId);

        when(taskBatchRepository.findAllById(anyIterable())).thenReturn(List.of(b1));

        DatasetEntity d = new DatasetEntity();
        d.setId(datasetId);
        d.setProjectId(projectId);

        when(datasetRepository.findAllById(anyIterable())).thenReturn(List.of(d));

        String body = """
                {
                  "tasks": [
                    {"batchId": "%s", "payloadJson": "{}", "status": "READY"}
                  ]
                }
                """.formatted(batch1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TaskEntity>> captor = ArgumentCaptor.forClass(List.class);
        when(taskRepository.saveAll(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/internal/tasks/bulk")
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        TaskEntity t = captor.getValue().get(0);
        assertThat(t.getStatus()).isEqualTo(TaskStatus.NEW);
    }
}
