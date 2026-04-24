package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiConflictException;
import com.datacrowd.core.api.ApiForbiddenException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewWorkflowServiceTest {

    @Mock AnswerRepository        answerRepository;
    @Mock ReviewRepository        reviewRepository;
    @Mock TaskRepository          taskRepository;
    @Mock ProjectRepository       projectRepository;
    @Mock PointsService           pointsService;
    @Mock WorkerProfileRepository workerProfileRepository;
    @Mock AuditService            auditService;
    @Mock MetricsService          metricsService;

    @InjectMocks ReviewWorkflowService reviewWorkflowService;

    // Вспомогательный метод — создаём AnswerEntity уже с task через mock
    private AnswerEntity makeAnswer(UUID answerId, UUID workerId,
                                    UUID taskId, TaskEntity task) {
        AnswerEntity answer = mock(AnswerEntity.class);
        when(answer.getId()).thenReturn(answerId);
        when(answer.getTaskId()).thenReturn(taskId);
        when(answer.getUserId()).thenReturn(workerId);
        when(answer.getStatus()).thenReturn(AnswerStatus.SUBMITTED);
        when(answer.getTask()).thenReturn(task);
        return answer;
    }

    @Test
    void approve_finalizes_whenApprovalsReachRequired() {
        UUID answerId   = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID workerId   = UUID.randomUUID();
        UUID taskId     = UUID.randomUUID();
        UUID projectId  = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.IN_REVIEW);

        AnswerEntity answer = makeAnswer(answerId, workerId, taskId, task);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setReviewersCount(1);
        project.setRewardPoints(10);

        when(answerRepository.findByIdWithTask(answerId)).thenReturn(Optional.of(answer));
        when(reviewRepository.existsByAnswerIdAndReviewerId(answerId, reviewerId)).thenReturn(false);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(reviewRepository.countByAnswerIdAndDecision(answerId, ReviewDecision.APPROVED))
                .thenReturn(1L);
        when(pointsService.awardTaskApprovedOnce(workerId, taskId, 10)).thenReturn(10);

        ReviewWorkflowService.DecisionResult result =
                reviewWorkflowService.approve(answerId, reviewerId, "good");

        assertThat(result.pointsAwarded()).isEqualTo(10);
        assertThat(result.task().getStatus()).isEqualTo(TaskStatus.APPROVED);
        verify(metricsService).incrementTasksApproved();
    }

    @Test
    void reject_decreasesTrustScore_byTen() {
        UUID answerId   = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID workerId   = UUID.randomUUID();
        UUID taskId     = UUID.randomUUID();
        UUID projectId  = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.IN_REVIEW);

        AnswerEntity answer = makeAnswer(answerId, workerId, taskId, task);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setReviewersCount(1);

        WorkerProfileEntity profile = new WorkerProfileEntity(workerId);
        profile.setTrustScore(80);

        when(answerRepository.findByIdWithTask(answerId)).thenReturn(Optional.of(answer));
        when(reviewRepository.existsByAnswerIdAndReviewerId(answerId, reviewerId)).thenReturn(false);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.of(profile));

        reviewWorkflowService.reject(answerId, reviewerId, "bad");

        assertThat(profile.getTrustScore()).isEqualTo(70);
        verify(metricsService).incrementTasksRejected();
    }

    @Test
    void reject_trustScore_doesNotGoBelowZero() {
        UUID answerId   = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID workerId   = UUID.randomUUID();
        UUID taskId     = UUID.randomUUID();
        UUID projectId  = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.IN_REVIEW);

        AnswerEntity answer = makeAnswer(answerId, workerId, taskId, task);
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setReviewersCount(1);

        WorkerProfileEntity profile = new WorkerProfileEntity(workerId);
        profile.setTrustScore(5);

        when(answerRepository.findByIdWithTask(answerId)).thenReturn(Optional.of(answer));
        when(reviewRepository.existsByAnswerIdAndReviewerId(answerId, reviewerId)).thenReturn(false);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.of(profile));

        reviewWorkflowService.reject(answerId, reviewerId, "bad");

        assertThat(profile.getTrustScore()).isEqualTo(0);
    }

    @Test
    void approve_throwsForbidden_whenReviewerIsAnswerOwner() {
        UUID sameUser  = UUID.randomUUID();
        UUID answerId  = UUID.randomUUID();
        UUID taskId    = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.IN_REVIEW);

        AnswerEntity answer = makeAnswer(answerId, sameUser, taskId, task);

        when(answerRepository.findByIdWithTask(answerId)).thenReturn(Optional.of(answer));

        assertThatThrownBy(() ->
                reviewWorkflowService.approve(answerId, sameUser, "self"))
                .isInstanceOf(ApiForbiddenException.class);
    }

    @Test
    void approve_throwsConflict_whenAlreadyReviewed() {
        UUID answerId   = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID workerId   = UUID.randomUUID();
        UUID taskId     = UUID.randomUUID();
        UUID projectId  = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.IN_REVIEW);

        AnswerEntity answer = makeAnswer(answerId, workerId, taskId, task);

        when(answerRepository.findByIdWithTask(answerId)).thenReturn(Optional.of(answer));
        when(reviewRepository.existsByAnswerIdAndReviewerId(answerId, reviewerId)).thenReturn(true);

        assertThatThrownBy(() ->
                reviewWorkflowService.approve(answerId, reviewerId, "again"))
                .isInstanceOf(ApiConflictException.class);
    }
}