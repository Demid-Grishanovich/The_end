package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiConflictException;
import com.datacrowd.core.api.ApiForbiddenException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)  // НОВОЕ
class WorkerTaskServiceTest {

    @Mock TaskRepository          taskRepository;
    @Mock ProjectRepository       projectRepository;
    @Mock AnswerRepository        answerRepository;
    @Mock PointsService           pointsService;
    @Mock StorageService          storageService;
    @Mock WorkerProfileRepository workerProfileRepository;
    @Mock AuditService            auditService;
    @Mock MetricsService          metricsService;

    @Mock ObjectMapper objectMapper;

    @InjectMocks WorkerTaskService workerTaskService;

    @Captor ArgumentCaptor<AnswerEntity>        answerCaptor;
    @Captor ArgumentCaptor<TaskEntity>          taskCaptor;
    @Captor ArgumentCaptor<WorkerProfileEntity> profileCaptor;

    // -----------------------------------------------------------------------
    // Существующий тест — нельзя взять две задачи
    // -----------------------------------------------------------------------

    @Test
    void lock_throwsConflict_whenWorkerAlreadyHasAnotherLockedTask() {
        UUID workerId      = UUID.randomUUID();
        UUID lockedTaskId  = UUID.randomUUID();
        UUID requestedId   = UUID.randomUUID();

        TaskEntity alreadyLocked = new TaskEntity();
        alreadyLocked.setId(lockedTaskId);
        alreadyLocked.setStatus(TaskStatus.LOCKED);
        alreadyLocked.setLockedByUserId(workerId);

        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.empty());
        when(taskRepository.findFirstByLockedByUserIdAndStatus(workerId, TaskStatus.LOCKED))
                .thenReturn(Optional.of(alreadyLocked));

        assertThatThrownBy(() -> workerTaskService.lock(requestedId, workerId))
                .isInstanceOf(ApiConflictException.class)
                .hasMessageContaining("already has a locked task");
    }

    // -----------------------------------------------------------------------
    // НОВЫЙ: Trust Score < 30 блокирует получение задачи
    // -----------------------------------------------------------------------

    @Test
    void nextTask_throwsForbidden_whenTrustScoreTooLow() {
        UUID workerId = UUID.randomUUID();

        WorkerProfileEntity profile = new WorkerProfileEntity(workerId);
        profile.setTrustScore(20); // ниже порога 30

        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> workerTaskService.nextTask(workerId))
                .isInstanceOf(ApiForbiddenException.class)
                .hasMessageContaining("trust score");
    }

    // -----------------------------------------------------------------------
    // НОВЫЙ: Trust Score = 30 (граничное значение) — задача выдаётся
    // -----------------------------------------------------------------------

    @Test
    void nextTask_allowsAccess_whenTrustScoreExactlyAtThreshold() {
        UUID workerId = UUID.randomUUID();

        WorkerProfileEntity profile = new WorkerProfileEntity(workerId);
        profile.setTrustScore(30); // ровно на пороге — должен пройти

        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.of(profile));
        when(taskRepository.findFirstByLockedByUserIdAndStatus(workerId, TaskStatus.LOCKED))
                .thenReturn(Optional.empty());
        when(taskRepository.findNextAvailableTasks(any()))
                .thenReturn(java.util.List.of());

        // Не должен бросить исключение
        assertThatCode(() -> workerTaskService.nextTask(workerId))
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // НОВЫЙ: submit — слишком быстрый ответ штрафует воркера
    // -----------------------------------------------------------------------

    @Test
    void submit_throwsConflict_andDecreasesTrustScore_whenAnswerTooFast() {
        UUID taskId   = UUID.randomUUID();
        UUID workerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.LOCKED);
        task.setLockedByUserId(workerId);
        // Задача заблокирована 0 секунд назад
        task.setLockedAt(Instant.now());
        task.setPayloadJson(null);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setMinAnswerSeconds(5); // минимум 5 секунд
        project.setReviewersCount(1);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(workerProfileRepository.findById(workerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                workerTaskService.submit(taskId, workerId, "{\"label\":\"positive\"}"))
                .isInstanceOf(ApiConflictException.class)
                .hasMessageContaining("too fast");

        // Должен уменьшить trust score
        verify(workerProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getTrustScore()).isEqualTo(85); // 100 - 15
        verify(metricsService).incrementBotDetected();
    }

    // -----------------------------------------------------------------------
    // НОВЫЙ: submit без ревьюеров — задача авто-апрувится
    // -----------------------------------------------------------------------

    @Test
    void submit_autoApproves_whenReviewersCountIsZero() {
        UUID taskId    = UUID.randomUUID();
        UUID workerId  = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProjectId(projectId);
        task.setStatus(TaskStatus.LOCKED);
        task.setLockedByUserId(workerId);
        task.setLockedAt(Instant.now().minusSeconds(30)); // 30 сек назад — достаточно
        task.setPayloadJson(null);

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setMinAnswerSeconds(0); // нет минимума
        project.setReviewersCount(0);  // авто-апрув
        project.setRewardPoints(10);

        AnswerEntity savedAnswer = new AnswerEntity();
        savedAnswer.setId(UUID.randomUUID());
        savedAnswer.setStatus(AnswerStatus.SUBMITTED);

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(answerRepository.save(any())).thenReturn(savedAnswer);
        when(pointsService.awardTaskApprovedOnce(eq(workerId), eq(taskId), eq(10)))
                .thenReturn(10);

        WorkerTaskService.SubmitResult result =
                workerTaskService.submit(taskId, workerId, "{\"label\":\"positive\"}");

        assertThat(result.task().getStatus()).isEqualTo(TaskStatus.APPROVED);
        assertThat(result.pointsAwarded()).isEqualTo(10);

        verify(metricsService).incrementTasksSubmitted();
        verify(metricsService).incrementTasksApproved();
        verify(metricsService).decrementActiveLocks();
    }
}