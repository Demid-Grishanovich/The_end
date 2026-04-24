package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiConflictException;
import com.datacrowd.core.api.ApiForbiddenException;
import com.datacrowd.core.api.ApiNotFoundException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.AnswerRepository;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.repo.ReviewRepository;
import com.datacrowd.core.repo.TaskRepository;
import com.datacrowd.core.repo.WorkerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.Optional;
import java.util.UUID;

@Service
public class ReviewWorkflowService {

    private final AnswerRepository        answerRepository;
    private final ReviewRepository        reviewRepository;
    private final TaskRepository          taskRepository;
    private final ProjectRepository       projectRepository;
    private final PointsService           pointsService;
    private final WorkerProfileRepository workerProfileRepository;

    // Константы системы рейтинга
    private static final int TRUST_SCORE_START   = 100; // стартовый рейтинг нового воркера
    private static final int TRUST_SCORE_MAX     = 100; // максимум
    private static final int TRUST_SCORE_MIN     = 0;   // минимум
    private static final int TRUST_SCORE_PENALTY = 10;  // штраф за reject
    private static final int TRUST_SCORE_REWARD  = 2;   // бонус за approve (растёт медленнее)
    private static final int TRUST_SCORE_BLOCK   = 30;
    private final AuditService auditService;
    private final MetricsService metricsService;
    public ReviewWorkflowService(AnswerRepository answerRepository,
                                 ReviewRepository reviewRepository,
                                 TaskRepository taskRepository,
                                 ProjectRepository projectRepository,
                                 PointsService pointsService,
                                 WorkerProfileRepository workerProfileRepository, AuditService auditService,MetricsService metricsService  ) {
        this.answerRepository        = answerRepository;
        this.reviewRepository        = reviewRepository;
        this.taskRepository          = taskRepository;
        this.projectRepository       = projectRepository;
        this.pointsService           = pointsService;
        this.workerProfileRepository = workerProfileRepository;
        this.auditService            = auditService;
        this.metricsService = metricsService;
    }

    @Transactional(readOnly = true)
    public Optional<AnswerEntity> nextForReview(UUID reviewerId) {
        return answerRepository.findNextForReview(reviewerId);
    }

    @Transactional
    public DecisionResult approve(UUID answerId, UUID reviewerId, String comment) {
        return decide(answerId, reviewerId, ReviewDecision.APPROVED, comment);
    }

    @Transactional
    public DecisionResult reject(UUID answerId, UUID reviewerId, String comment) {
        return decide(answerId, reviewerId, ReviewDecision.REJECTED, comment);
    }

    private DecisionResult decide(UUID answerId,
                                  UUID reviewerId,
                                  ReviewDecision decision,
                                  String comment) {

        // 1. Загружаем ответ с задачей
        AnswerEntity answer = answerRepository.findByIdWithTask(answerId)
                .orElseThrow(() -> new ApiNotFoundException("Answer not found: " + answerId));

        TaskEntity task = answer.getTask();
        if (task == null) {
            task = taskRepository.findById(answer.getTaskId())
                    .orElseThrow(() -> new ApiNotFoundException("Task not found: " + answer.getTaskId()));
        }

        // 2. Базовые проверки
        if (answer.getUserId().equals(reviewerId)) {
            throw new ApiForbiddenException("Reviewer cannot review own answer");
        }
        if (answer.getStatus() != AnswerStatus.SUBMITTED) {
            throw new ApiConflictException(
                    "Answer must be SUBMITTED to review (status=" + answer.getStatus() + ")");
        }
        if (task.getStatus() != TaskStatus.IN_REVIEW) {
            throw new ApiConflictException(
                    "Task must be IN_REVIEW to review answers (status=" + task.getStatus() + ")");
        }
        if (reviewRepository.existsByAnswerIdAndReviewerId(answerId, reviewerId)) {
            throw new ApiConflictException("Reviewer already reviewed this answer");
        }

        // 3. Сохраняем решение ревьюера
        ReviewEntity review = new ReviewEntity();
        review.setAnswerId(answerId);
        review.setReviewerId(reviewerId);
        review.setDecision(decision);
        review.setComment(comment);
        reviewRepository.save(review);

        // 4. Загружаем проект для настроек
        UUID projectId = task.getProjectId();
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiNotFoundException("Project not found: " + projectId));

        int required = (project.getReviewersCount() == null ? 1 : project.getReviewersCount());
        if (required < 1) required = 1;

        // 5. Обрабатываем REJECT
        if (decision == ReviewDecision.REJECTED) {
            answer.setStatus(AnswerStatus.REJECTED);
            answerRepository.save(answer);

            task.setStatus(TaskStatus.NEW);
            task.setLockedByUserId(null);
            task.setLockedAt(null);
            taskRepository.save(task);

            // Штраф за плохую работу: -10 к рейтингу
            updateTrustScore(answer.getUserId(), -TRUST_SCORE_PENALTY);
            auditService.log(reviewerId, AuditLogEntity.ANSWER_REJECTED, "ANSWER", answerId);
            metricsService.incrementTasksRejected();


            return new DecisionResult(answer, task, 0, 0L, required);
        }

        // 6. Обрабатываем APPROVE
        long approvals = reviewRepository.countByAnswerIdAndDecision(answerId, ReviewDecision.APPROVED);

        int awarded = 0;
        if (approvals >= required) {
            answer.setStatus(AnswerStatus.APPROVED);
            answerRepository.save(answer);

            task.setStatus(TaskStatus.APPROVED);
            taskRepository.save(task);

            int reward = (project.getRewardPoints() == null ? 0 : project.getRewardPoints());
            awarded = pointsService.awardTaskApprovedOnce(answer.getUserId(), task.getId(), reward);

            // Бонус за хорошую работу: +5 к рейтингу
            updateTrustScore(answer.getUserId(), +TRUST_SCORE_REWARD);
            auditService.log(reviewerId, AuditLogEntity.ANSWER_APPROVED, "ANSWER", answerId);
            metricsService.incrementTasksApproved();
        }

        return new DecisionResult(answer, task, awarded, approvals, required);
    }

    // -----------------------------------------------------------------------
    // Trust Score: универсальный метод изменения рейтинга
    // -----------------------------------------------------------------------

    private void updateTrustScore(UUID workerId, int delta) {
        WorkerProfileEntity profile = workerProfileRepository.findById(workerId)
                .orElseGet(() -> {
                    WorkerProfileEntity newProfile = new WorkerProfileEntity(workerId);
                    newProfile.setTrustScore(TRUST_SCORE_START);
                    return newProfile;
                });

        int current  = profile.getTrustScore() != null ? profile.getTrustScore() : TRUST_SCORE_START;
        int newScore = Math.min(TRUST_SCORE_MAX, Math.max(TRUST_SCORE_MIN, current + delta));

        profile.setTrustScore(newScore);
        workerProfileRepository.save(profile);
    }

    // -----------------------------------------------------------------------
    // Result record
    // -----------------------------------------------------------------------
    public record DecisionResult(
            AnswerEntity answer,
            TaskEntity   task,
            int          pointsAwarded,
            long         approvals,
            int          requiredApprovals
    ) {}
}