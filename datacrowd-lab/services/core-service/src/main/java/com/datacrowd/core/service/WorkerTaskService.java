package com.datacrowd.core.service;


import com.datacrowd.core.api.ApiConflictException;
import com.datacrowd.core.api.ApiForbiddenException;
import com.datacrowd.core.api.ApiNotFoundException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.AnswerRepository;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.repo.TaskRepository;
import com.datacrowd.core.repo.WorkerProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkerTaskService {

    private static final int TRUST_SCORE_BLOCK_THRESHOLD = 30;
    private static final Logger log = LoggerFactory.getLogger(WorkerTaskService.class);

    private final AuditService auditService;
    private final TaskRepository          taskRepository;
    private final ProjectRepository       projectRepository;
    private final AnswerRepository        answerRepository;
    private final PointsService           pointsService;
    private final StorageService          storageService;
    private final WorkerProfileRepository workerProfileRepository;
    private final ObjectMapper            objectMapper;
    private final MetricsService metricsService;

    public WorkerTaskService(TaskRepository taskRepository,
                             ProjectRepository projectRepository,
                             AnswerRepository answerRepository,
                             PointsService pointsService,
                             StorageService storageService,
                             WorkerProfileRepository workerProfileRepository,
                             ObjectMapper objectMapper,
                             AuditService auditService , MetricsService metricsService) {
        this.taskRepository          = taskRepository;
        this.projectRepository       = projectRepository;
        this.answerRepository        = answerRepository;
        this.pointsService           = pointsService;
        this.storageService          = storageService;
        this.workerProfileRepository = workerProfileRepository;
        this.objectMapper            = objectMapper;
        this.auditService            = auditService;
        this.metricsService = metricsService;
    }

    // -----------------------------------------------------------------------
    // Получить следующую задачу
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<TaskEntity> nextTask(UUID workerUserId) {

        checkTrustScore(workerUserId);

        Optional<TaskEntity> mine = taskRepository
                .findFirstByLockedByUserIdAndStatus(workerUserId, TaskStatus.LOCKED);
        if (mine.isPresent()) return mine;

        var list = taskRepository.findNextAvailableTasks(PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // -----------------------------------------------------------------------
    // Заблокировать задачу
    // -----------------------------------------------------------------------

    @Transactional
    public TaskEntity lock(UUID taskId, UUID workerUserId) {

        checkTrustScore(workerUserId);

        Optional<TaskEntity> mine = taskRepository
                .findFirstByLockedByUserIdAndStatus(workerUserId, TaskStatus.LOCKED);
        if (mine.isPresent() && !mine.get().getId().equals(taskId)) {
            throw new ApiConflictException(
                    "Worker already has a locked task: " + mine.get().getId());
        }

        int updated = taskRepository.lockIfAvailable(taskId, workerUserId, Instant.now());
        if (updated == 1) {
            TaskEntity locked = taskRepository.findById(taskId)
                    .orElseThrow(() -> new ApiNotFoundException("Task not found: " + taskId));
            metricsService.incrementActiveLocks();
            auditService.log(workerUserId, AuditLogEntity.TASK_LOCKED, "TASK", taskId); 
            return locked;
        }

        TaskEntity existing = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiNotFoundException("Task not found: " + taskId));

        if (existing.getLockedByUserId() != null
                && existing.getLockedByUserId().equals(workerUserId)) {
            return existing;
        }

        throw new ApiConflictException(
                "Task is not available for lock (status=" + existing.getStatus() + ")");
    }

    // -----------------------------------------------------------------------
    // Разблокировать задачу
    // -----------------------------------------------------------------------

    @Transactional
    public TaskEntity unlock(UUID taskId, UUID workerUserId) {
        int updated = taskRepository.unlockOwned(taskId, workerUserId);
        if (updated == 1) {
            metricsService.decrementActiveLocks();
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
        throw new ApiConflictException(
                "Task cannot be unlocked in current status: " + existing.getStatus());
    }

    // -----------------------------------------------------------------------
    // Отправить ответ на задачу
    // -----------------------------------------------------------------------

    @Transactional
    public SubmitResult submit(UUID taskId, UUID workerUserId, String answerJson) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiNotFoundException("Task not found: " + taskId));

        if (task.getLockedByUserId() == null
                || !task.getLockedByUserId().equals(workerUserId)) {
            throw new ApiForbiddenException("Only lock owner can submit");
        }
        if (task.getStatus() != TaskStatus.LOCKED) {
            throw new ApiConflictException(
                    "Task must be LOCKED to submit (status=" + task.getStatus() + ")");
        }

        ProjectEntity project = projectRepository.findById(task.getProjectId())
                .orElseThrow(() -> new ApiNotFoundException(
                        "Project not found: " + task.getProjectId()));

        if (task.getLockedAt() != null && project.getMinAnswerSeconds() > 0) {
            long secondsSpent = java.time.Duration.between(
                    task.getLockedAt(), Instant.now()).getSeconds();
            int minRequired = project.getMinAnswerSeconds();

            if (secondsSpent < minRequired) {
                updateTrustScoreForBot(workerUserId);
                throw new ApiConflictException(
                        "Answer submitted too fast (" + secondsSpent + "s). " +
                                "Minimum required: " + minRequired + "s. " +
                                "Suspicious activity detected, trust score decreased by 15."
                );
            }
        }
        if (task.getPayloadJson() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode payload =
                        objectMapper.readTree(task.getPayloadJson());

                com.fasterxml.jackson.databind.JsonNode honeypotNode =
                        payload.get("isHoneypot");

                if (honeypotNode != null && honeypotNode.asBoolean()) {
                    com.fasterxml.jackson.databind.JsonNode expectedNode =
                            payload.get("expectedAnswer");
                    String expected = (expectedNode != null && !expectedNode.isNull())
                            ? expectedNode.asText() : null;

                    String workerLabel = null;
                    try {
                        com.fasterxml.jackson.databind.JsonNode answerNode =
                                objectMapper.readTree(answerJson);
                        com.fasterxml.jackson.databind.JsonNode labelNode =
                                answerNode.get("label");
                        if (labelNode != null) workerLabel = labelNode.asText();
                    } catch (Exception ignored) {}

                    if (expected != null && workerLabel != null
                            && !expected.equalsIgnoreCase(workerLabel)) {
                        updateTrustScoreForHoneypot(workerUserId);

                        task.setStatus(TaskStatus.NEW);
                        task.setLockedByUserId(null);
                        task.setLockedAt(null);
                        taskRepository.save(task);

                        AnswerEntity fakeAnswer = new AnswerEntity();
                        fakeAnswer.setTaskId(taskId);
                        fakeAnswer.setUserId(workerUserId);
                        fakeAnswer.setAnswerJson(answerJson);
                        fakeAnswer.setStatus(AnswerStatus.REJECTED);
                        return new SubmitResult(task, fakeAnswer, 0);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        AnswerStatus initialStatus = AnswerStatus.SUBMITTED;

        if (task.getPayloadJson() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode payload =
                        objectMapper.readTree(task.getPayloadJson());

                com.fasterxml.jackson.databind.JsonNode aiLabelNode =
                        payload.get("aiSuggestedLabel");
                com.fasterxml.jackson.databind.JsonNode aiConfidenceNode =
                        payload.get("aiConfidence");

                if (aiLabelNode != null && !aiLabelNode.isNull()
                        && aiConfidenceNode != null
                        && aiConfidenceNode.asDouble() > 0.85) {

                    String aiLabel = aiLabelNode.asText();

                    String workerLabel = null;
                    try {
                        com.fasterxml.jackson.databind.JsonNode answerNode =
                                objectMapper.readTree(answerJson);
                        com.fasterxml.jackson.databind.JsonNode labelNode =
                                answerNode.get("label");
                        if (labelNode != null) workerLabel = labelNode.asText();
                    } catch (Exception ignored) {}

                    if (workerLabel != null) {
                        if (aiLabel.equalsIgnoreCase(workerLabel)) {
                            log.debug("[AutoQA] AI and worker agree: label={}", workerLabel);
                        } else {
                            log.info("[AutoQA] Disagreement detected: ai={} worker={} taskId={}",
                                    aiLabel, workerLabel, taskId);
                        }
                    }
                }
            } catch (Exception ignored) {

            }
        }

        AnswerEntity answer = new AnswerEntity();
        answer.setTaskId(taskId);
        answer.setUserId(workerUserId);
        answer.setAnswerJson(answerJson);
        answer.setStatus(initialStatus);
        answer = answerRepository.save(answer);
        auditService.log(workerUserId, AuditLogEntity.TASK_SUBMITTED, "TASK", taskId);
        metricsService.incrementTasksSubmitted();

        int reviewersCount = (project.getReviewersCount() == null
                ? 0 : project.getReviewersCount());
        if (reviewersCount < 0) reviewersCount = 0;

        task.setLockedByUserId(null);
        task.setLockedAt(null);

        int awarded = 0;
        if (reviewersCount == 0) {
            task.setStatus(TaskStatus.APPROVED);
            answer.setStatus(AnswerStatus.APPROVED);
            answerRepository.save(answer);

            int reward = (project.getRewardPoints() == null ? 0 : project.getRewardPoints());
            awarded = pointsService.awardTaskApprovedOnce(workerUserId, taskId, reward);
            metricsService.incrementTasksApproved();
            metricsService.decrementActiveLocks();
        } else {
            task.setStatus(TaskStatus.IN_REVIEW);
        }

        taskRepository.save(task);
        return new SubmitResult(task, answer, awarded);
    }

    @Transactional(readOnly = true)
    public Optional<TaskEntity> nextTaskForProject(UUID workerUserId, UUID projectId) {
        checkTrustScore(workerUserId);

        Optional<TaskEntity> mine = taskRepository
                .findFirstByLockedByUserIdAndStatus(workerUserId, TaskStatus.LOCKED);
        if (mine.isPresent()) {
            if (projectId.equals(mine.get().getProjectId())) return mine;
            throw new ApiConflictException("You have a locked task in another project. Finish it first.");
        }

        var list = taskRepository.findNextAvailableByProject(projectId, PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }


    // -----------------------------------------------------------------------
    // Получить путь к файлу-ассету задачи (для стриминга картинок/аудио)
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Path resolveTaskAssetPath(UUID taskId, UUID requesterUserId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiNotFoundException("Task not found: " + taskId));

        boolean isLockedByOther = task.getLockedByUserId() != null
                && !task.getLockedByUserId().equals(requesterUserId);

        if (isLockedByOther) {
            // Дополнительно проверяем — может это ревьюер смотрит уже сданную задачу
            TaskStatus s = task.getStatus();
            boolean isReviewable = s == TaskStatus.SUBMITTED
                    || s == TaskStatus.IN_REVIEW
                    || s == TaskStatus.APPROVED
                    || s == TaskStatus.REJECTED;
            if (!isReviewable) {
                throw new ApiForbiddenException("Task is locked by another user");
            }
        }

        String payload = task.getPayloadJson();
        if (payload == null || payload.isBlank()) {
            throw new ApiNotFoundException("Task has no payload");
        }

        String rel;
        try {
            JsonNode n = objectMapper.readTree(payload);

            // Сначала ищем в корне
            JsonNode v = n.get("assetRelPath");
            if (v == null || v.isNull() || v.asText().isBlank()) {
                v = n.get("file");
            }
            if (v == null || v.isNull() || v.asText().isBlank()) {
                v = n.get("url");
            }
            if (v == null || v.isNull() || v.asText().isBlank()) {
                v = n.get("imageUrl");
            }
            if (v == null || v.isNull() || v.asText().isBlank()) {
                v = n.get("filePath");
            }

            // Если в корне не нашли — ищем внутри data{}
            if (v == null || v.isNull() || v.asText().isBlank()) {
                JsonNode data = n.get("data");
                if (data != null && !data.isNull()) {
                    v = data.get("assetRelPath");
                    if (v == null || v.isNull() || v.asText().isBlank()) {
                        v = data.get("file");
                    }
                    if (v == null || v.isNull() || v.asText().isBlank()) {
                        v = data.get("url");
                    }
                    if (v == null || v.isNull() || v.asText().isBlank()) {
                        v = data.get("imageUrl");
                    }
                    if (v == null || v.isNull() || v.asText().isBlank()) {
                        v = data.get("image_url");
                    }
                    if (v == null || v.isNull() || v.asText().isBlank()) {
                        v = data.get("filePath");
                    }
                }
            }

            rel = (v == null || v.isNull()) ? null : v.asText();
        } catch (Exception e) {
            throw new ApiConflictException("Task payload is not valid JSON");
        }

        if (rel == null || rel.isBlank()) {
            throw new ApiNotFoundException("Task does not reference an asset");
        }

        Path p = storageService.resolveDatasetAsset(task.getDatasetId(), rel);
        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            throw new ApiNotFoundException("Asset file not found");
        }
        return p;
    }

    // -----------------------------------------------------------------------
    // Проверка Trust Score
    // -----------------------------------------------------------------------

    private void checkTrustScore(UUID workerUserId) {
        workerProfileRepository.findById(workerUserId).ifPresent(profile -> {
            int score = profile.getTrustScore() != null ? profile.getTrustScore() : 100;
            if (score < TRUST_SCORE_BLOCK_THRESHOLD) {
                throw new ApiForbiddenException(
                        "Your trust score (" + score + "/100) is too low to receive tasks. " +
                                "Minimum required: " + TRUST_SCORE_BLOCK_THRESHOLD + ". " +
                                "Complete quality work to restore your rating."
                );
            }
        });
    }
    private void updateTrustScoreForHoneypot(UUID workerId) {
        WorkerProfileEntity profile = workerProfileRepository.findById(workerId)
                .orElseGet(() -> {
                    WorkerProfileEntity p = new WorkerProfileEntity(workerId);
                    p.setTrustScore(100);
                    return p;
                });
        int current  = profile.getTrustScore() != null ? profile.getTrustScore() : 100;
        int newScore = Math.max(0, current - 20);
        profile.setTrustScore(newScore);
        workerProfileRepository.save(profile);
        metricsService.incrementHoneypotFailed();
        auditService.log(workerId, AuditLogEntity.HONEYPOT_FAILED, "WORKER", workerId,
                "Trust score decreased by 20 (honeypot failed)", null);

    }
    private void updateTrustScoreForBot(UUID workerId) {
        WorkerProfileEntity profile = workerProfileRepository.findById(workerId)
                .orElseGet(() -> {
                    WorkerProfileEntity p = new WorkerProfileEntity(workerId);
                    p.setTrustScore(100);
                    return p;
                });
        int current  = profile.getTrustScore() != null ? profile.getTrustScore() : 100;
        int newScore = Math.max(0, current - 15);
        profile.setTrustScore(newScore);
        workerProfileRepository.save(profile);
        metricsService.incrementBotDetected();
        auditService.log(workerId, AuditLogEntity.BOT_DETECTED, "WORKER", workerId,
                "Trust score decreased by 15 (bot detection)", null);

    }
    // -----------------------------------------------------------------------
    // Result record
    // -----------------------------------------------------------------------
    public record SubmitResult(
            TaskEntity   task,
            AnswerEntity answer,
            int          pointsAwarded
    ) {}
}