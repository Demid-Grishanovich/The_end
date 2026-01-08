package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiConflictException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.AnswerRepository;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.repo.ReviewRepository;
import com.datacrowd.core.repo.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewWorkflowServiceTest {

    @Mock AnswerRepository answerRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock PointsService pointsService;

    @InjectMocks ReviewWorkflowService reviewWorkflowService;

    @Test
    void approve_finalizes_whenApprovalsReachRequired_andAwardsPoints() {
        UUID answerId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.IN_REVIEW);

        AnswerEntity answer = new AnswerEntity();
        answer.setId(answerId);
        answer.setTaskId(taskId);
        answer.setUserId(workerId);
        answer.setStatus(AnswerStatus.SUBMITTED);
        // attach task (simulating fetch join)
        // Note: no setter for task, so we rely on repository fetch join and getTask() returning non-null in real runtime.
        // In unit test, we just stub taskRepository fallback.

        when(answerRepository.findByIdWithTask(answerId)).thenReturn(Optional.of(answer));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(reviewRepository.existsByAnswerIdAndReviewerId(answerId, reviewerId)).thenReturn(false);
        when(reviewRepository.countByAnswerIdAndDecision(answerId, ReviewDecision.APPROVED)).thenReturn(1L);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setReviewersCount(1);
        project.setRewardPoints(10);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        when(pointsService.awardTaskApprovedOnce(workerId, taskId, 10)).thenReturn(10);

        ReviewWorkflowService.DecisionResult res = reviewWorkflowService.approve(answerId, reviewerId, "ok");

        assertThat(res.task().getStatus()).isEqualTo(TaskStatus.APPROVED);
        assertThat(res.answer().getStatus()).isEqualTo(AnswerStatus.APPROVED);
        assertThat(res.pointsAwarded()).isEqualTo(10);
        assertThat(res.approvals()).isEqualTo(1);
        assertThat(res.requiredApprovals()).isEqualTo(1);

        verify(reviewRepository).save(any(ReviewEntity.class));
    }

    @Test
    void reject_setsTaskBackToNew_andMarksAnswerRejected() {
        UUID answerId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.IN_REVIEW);

        AnswerEntity answer = new AnswerEntity();
        answer.setId(answerId);
        answer.setTaskId(taskId);
        answer.setUserId(workerId);
        answer.setStatus(AnswerStatus.SUBMITTED);

        when(answerRepository.findByIdWithTask(answerId)).thenReturn(Optional.of(answer));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(reviewRepository.existsByAnswerIdAndReviewerId(answerId, reviewerId)).thenReturn(false);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setReviewersCount(2);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ReviewWorkflowService.DecisionResult res = reviewWorkflowService.reject(answerId, reviewerId, "bad");

        assertThat(res.task().getStatus()).isEqualTo(TaskStatus.NEW);
        assertThat(res.answer().getStatus()).isEqualTo(AnswerStatus.REJECTED);
        assertThat(res.pointsAwarded()).isEqualTo(0);

        verify(reviewRepository).save(any(ReviewEntity.class));
    }

    @Test
    void approve_throwsConflict_whenAlreadyReviewed() {
        UUID answerId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        AnswerEntity answer = new AnswerEntity();
        answer.setId(answerId);
        answer.setTaskId(taskId);
        answer.setUserId(UUID.randomUUID());
        answer.setStatus(AnswerStatus.SUBMITTED);

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(UUID.randomUUID());
        task.setStatus(TaskStatus.IN_REVIEW);

        when(answerRepository.findByIdWithTask(answerId)).thenReturn(Optional.of(answer));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(reviewRepository.existsByAnswerIdAndReviewerId(answerId, reviewerId)).thenReturn(true);

        assertThatThrownBy(() -> reviewWorkflowService.approve(answerId, reviewerId, null))
                .isInstanceOf(ApiConflictException.class)
                .hasMessageContaining("already reviewed");
    }
}
