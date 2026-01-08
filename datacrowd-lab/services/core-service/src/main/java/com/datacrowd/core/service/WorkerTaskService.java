package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiConflictException;
import com.datacrowd.core.api.ApiForbiddenException;
import com.datacrowd.core.api.ApiNotFoundException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.AnswerRepository;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.repo.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkerTaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final AnswerRepository answerRepository;
    private final PointsService pointsService;

    public WorkerTaskService(TaskRepository taskRepository,
                             ProjectRepository projectRepository,
                             AnswerRepository answerRepository,
                             PointsService pointsService) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.answerRepository = answerRepository;
        this.pointsService = pointsService;
    }

    /**
     * Returns an already LOCKED task for the worker (if any), otherwise the oldest available task.
     */
    @Transactional(readOnly = true)
    public Optional<TaskEntity> nextTask(UUID workerUserId) {
        Optional<TaskEntity> mine = taskRepository.findFirstByLockedByUserIdAndStatus(workerUserId, TaskStatus.LOCKED);
        if (mine.isPresent()) return mine;

        var list = taskRepository.findNextAvailableTasks(PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }


    @Transactional
    public TaskEntity lock(UUID taskId, UUID workerUserId) {
        // Enforce one active lock at a time (simple UX/fairness)
        Optional<TaskEntity> mine = taskRepository.findFirstByLockedByUserIdAndStatus(workerUserId, TaskStatus.LOCKED);
        if (mine.isPresent() && !mine.get().getId().equals(taskId)) {
            throw new ApiConflictException("Worker already has a locked task: " + mine.get().getId());
        }

        int updated = taskRepository.lockIfAvailable(taskId, workerUserId, Instant.now());
        if (updated == 1) {
            return taskRepository.findById(taskId)
                    .orElseThrow(() -> new ApiNotFoundException("Task not found: " + taskId));
        }

        TaskEntity existing = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiNotFoundException("Task not found: " + taskId));

        if (existing.getLockedByUserId() != null && existing.getLockedByUserId().equals(workerUserId)) {
            // Idempotent: already locked by this worker
            return existing;
        }

        throw new ApiConflictException("Task is not available for lock (status=" + existing.getStatus() + ")");
    }

    @Transactional
    public TaskEntity unlock(UUID taskId, UUID workerUserId) {
        int updated = taskRepository.unlockOwned(taskId, workerUserId);
        if (updated == 1) {
            return taskRepository.findById(taskId)
                    .orElseThrow(() -> new ApiNotFoundException("Task not found: " + taskId));
        }

        TaskEntity existing = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiNotFoundException("Task not found: " + taskId));

        if (existing.getLockedByUserId() == null) {
            throw new ApiConflictException("Task is not locked");
        }
        if (!existing.getLockedByUserId().equals(workerUserId)) {
            throw new ApiForbiddenException("Only lock owner can unlock");
        }
        throw new ApiConflictException("Task cannot be unlocked in current status: " + existing.getStatus());
    }

    @Transactional
    public SubmitResult submit(UUID taskId, UUID workerUserId, String answerJson) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiNotFoundException("Task not found: " + taskId));

        if (task.getLockedByUserId() == null || !task.getLockedByUserId().equals(workerUserId)) {
            throw new ApiForbiddenException("Only lock owner can submit");
        }
        if (task.getStatus() != TaskStatus.LOCKED) {
            throw new ApiConflictException("Task must be LOCKED to submit (status=" + task.getStatus() + ")");
        }

        AnswerEntity answer = new AnswerEntity();
        answer.setTaskId(taskId);
        answer.setUserId(workerUserId);
        answer.setAnswerJson(answerJson);
        answer.setStatus(AnswerStatus.SUBMITTED);
        answer = answerRepository.save(answer);

        ProjectEntity project = projectRepository.findById(task.getProjectId())
                .orElseThrow(() -> new ApiNotFoundException("Project not found: " + task.getProjectId()));

        int reviewersCount = (project.getReviewersCount() == null ? 0 : project.getReviewersCount());
        if (reviewersCount < 0) reviewersCount = 0;

        // Release lock
        task.setLockedByUserId(null);
        task.setLockedAt(null);

        int awarded = 0;
        if (reviewersCount == 0) {
            // Auto-approve path
            task.setStatus(TaskStatus.APPROVED);
            answer.setStatus(AnswerStatus.APPROVED);
            answerRepository.save(answer);

            int reward = (project.getRewardPoints() == null ? 0 : project.getRewardPoints());
            awarded = pointsService.awardTaskApprovedOnce(workerUserId, taskId, reward);
        } else {
            task.setStatus(TaskStatus.IN_REVIEW);
        }

        taskRepository.save(task);
        return new SubmitResult(task, answer, awarded);
    }

    public record SubmitResult(TaskEntity task, AnswerEntity answer, int pointsAwarded) {}
}
