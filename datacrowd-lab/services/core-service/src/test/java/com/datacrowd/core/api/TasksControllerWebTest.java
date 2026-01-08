package com.datacrowd.core.api;

import com.datacrowd.core.dto.SubmitTaskRequest;
import com.datacrowd.core.entity.AnswerEntity;
import com.datacrowd.core.entity.AnswerStatus;
import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.entity.TaskStatus;
import com.datacrowd.core.security.JwtPrincipal;
import com.datacrowd.core.service.WorkerTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TasksControllerWebTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    WorkerTaskService workerTaskService;

    UUID workerId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        workerTaskService = mock(WorkerTaskService.class);

        TasksController controller = new TasksController(workerTaskService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();

        workerId = UUID.randomUUID();
        var principal = new JwtPrincipal(workerId.toString(), "worker", "WORKER");
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_WORKER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void next_returns204_whenNoTasks() throws Exception {
        when(workerTaskService.nextTask(workerId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/core/tasks/next"))
                .andExpect(status().isNoContent());
    }

    @Test
    void lock_returns200_taskResponse() throws Exception {
        UUID taskId = UUID.randomUUID();
        TaskEntity t = new TaskEntity();
        t.setId(taskId);
        t.setStatus(TaskStatus.LOCKED);
        t.setLockedByUserId(workerId);

        when(workerTaskService.lock(taskId, workerId)).thenReturn(t);

        mockMvc.perform(post("/core/tasks/{id}/lock", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }

    @Test
    void submit_returns200_andPointsAwarded() throws Exception {
        UUID taskId = UUID.randomUUID();

        TaskEntity t = new TaskEntity();
        t.setId(taskId);
        t.setStatus(TaskStatus.APPROVED);

        AnswerEntity a = new AnswerEntity();
        a.setId(UUID.randomUUID());
        a.setStatus(AnswerStatus.APPROVED);

        when(workerTaskService.submit(eq(taskId), eq(workerId), anyString()))
                .thenReturn(new WorkerTaskService.SubmitResult(t, a, 10));

        SubmitTaskRequest req = new SubmitTaskRequest();
        req.answerJson = "{\"x\":1}";

        mockMvc.perform(post("/core/tasks/{id}/submit", taskId)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.answerId").value(a.getId().toString()))
                .andExpect(jsonPath("$.taskStatus").value("APPROVED"))
                .andExpect(jsonPath("$.pointsAwarded").value(10));
    }
}
