package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiConflictException;
import com.datacrowd.core.api.ApiForbiddenException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.AnswerRepository;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.repo.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerTaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock AnswerRepository answerRepository;
    @Mock PointsService pointsService;

    @InjectMocks WorkerTaskService workerTaskService;

    @Captor ArgumentCaptor<AnswerEntity> answerCaptor;
    @Captor ArgumentCaptor<TaskEntity> taskCaptor;

    @Test
    void lock_throwsConflict_whenWorkerAlreadyHasAnotherLockedTask() {
        UUID workerId = UUID.randomUUID();
        UUID lockedTaskId = UUID.randomUUID();
        UUID requestedId = UUID.randomUUID();

        TaskEntity alreadyLocked = new TaskEntity();
        alreadyLocked.setId(lockedTaskId);
        alreadyLocked.setLockedByUserId(workerId);
        alreadyLocked.setStatus(TaskStatus.LOCKED);

        when(taskRepository.findFirstByLockedByUserIdAndStatus(workerId, TaskStatus.LOCKED))
                .thenReturn(Optional.of(alreadyLocked));

        assertThatThrownBy(() -> workerTaskService.lock(requestedId, workerId))
                .isInstanceOf(ApiConflictException.class)
                .hasMessageContaining("already has a locked task");

        verify(taskRepository, never()).lockIfAvailable(any(), any(), any());
    }

    @Test
    void submit_throwsForbidden_whenNotLockOwner() {
        UUID taskId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setStatus(TaskStatus.LOCKED);
        task.setLockedByUserId(UUID.randomUUID());

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> workerTaskService.submit(taskId, workerId, "{}"))
                .isInstanceOf(ApiForbiddenException.class);
    }

    @Test
    void submit_autoApproves_whenReviewersCountIsZero_andAwardsPointsOnce() {
        UUID taskId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.LOCKED);
        task.setLockedByUserId(workerId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        AnswerEntity savedAnswer = new AnswerEntity();
        savedAnswer.setId(UUID.randomUUID());
        when(answerRepository.save(any(AnswerEntity.class))).thenReturn(savedAnswer);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setReviewersCount(0);
        project.setRewardPoints(10);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        when(pointsService.awardTaskApprovedOnce(workerId, taskId, 10)).thenReturn(10);

        WorkerTaskService.SubmitResult res = workerTaskService.submit(taskId, workerId, "{\"a\":1}");

        assertThat(res.task().getStatus()).isEqualTo(TaskStatus.APPROVED);
        assertThat(res.answer().getStatus()).isEqualTo(AnswerStatus.APPROVED);
        assertThat(res.pointsAwarded()).isEqualTo(10);
        assertThat(res.task().getLockedByUserId()).isNull();

        verify(answerRepository).save(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getTaskId()).isEqualTo(taskId);
        assertThat(answerCaptor.getValue().getUserId()).isEqualTo(workerId);
    }

    @Test
    void submit_setsTaskInReview_whenReviewersNeeded() {
        UUID taskId = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.LOCKED);
        task.setLockedByUserId(workerId);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(answerRepository.save(any(AnswerEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setReviewersCount(1);
        project.setRewardPoints(10);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        WorkerTaskService.SubmitResult res = workerTaskService.submit(taskId, workerId, "{\"ok\":true}");

        assertThat(res.task().getStatus()).isEqualTo(TaskStatus.IN_REVIEW);
        assertThat(res.answer().getStatus()).isEqualTo(AnswerStatus.SUBMITTED);
        assertThat(res.pointsAwarded()).isEqualTo(0);
    }
}
