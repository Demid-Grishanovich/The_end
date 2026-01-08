package com.datacrowd.core.api;

import com.datacrowd.core.entity.AnswerEntity;
import com.datacrowd.core.entity.AnswerStatus;
import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.entity.TaskStatus;
import com.datacrowd.core.security.JwtPrincipal;
import com.datacrowd.core.service.ReviewWorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReviewsControllerWebTest {

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    ReviewWorkflowService reviewWorkflowService;

    UUID reviewerId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        reviewWorkflowService = mock(ReviewWorkflowService.class);

        ReviewsController controller = new ReviewsController(reviewWorkflowService);

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();

        reviewerId = UUID.randomUUID();
        var principal = new JwtPrincipal(reviewerId.toString(), "reviewer", "WORKER");
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
    void next_returns204_whenNoAnswers() throws Exception {
        when(reviewWorkflowService.nextForReview(reviewerId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/core/reviews/next"))
                .andExpect(status().isNoContent());
    }

    @Test
    void approve_returns200_decisionResponse() throws Exception {
        UUID answerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        AnswerEntity a = new AnswerEntity();
        a.setId(answerId);
        a.setTaskId(taskId);
        a.setUserId(UUID.randomUUID());
        a.setStatus(AnswerStatus.APPROVED);

        TaskEntity t = new TaskEntity();
        t.setId(taskId);
        t.setStatus(TaskStatus.APPROVED);

        when(reviewWorkflowService.approve(eq(answerId), eq(reviewerId), any()))
                .thenReturn(new ReviewWorkflowService.DecisionResult(a, t, 10, 1L, 1));

        mockMvc.perform(post("/core/reviews/{answerId}/approve", answerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerId").value(answerId.toString()))
                .andExpect(jsonPath("$.taskId").value(taskId.toString()))
                .andExpect(jsonPath("$.answerStatus").value("APPROVED"))
                .andExpect(jsonPath("$.taskStatus").value("APPROVED"))
                .andExpect(jsonPath("$.pointsAwarded").value(10));
    }
}
