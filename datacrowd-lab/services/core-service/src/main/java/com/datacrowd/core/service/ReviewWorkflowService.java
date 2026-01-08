package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiConflictException;
import com.datacrowd.core.api.ApiForbiddenException;
import com.datacrowd.core.api.ApiNotFoundException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.AnswerRepository;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.repo.ReviewRepository;
import com.datacrowd.core.repo.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ReviewWorkflowService {

    private final AnswerRepository answerRepository;
    private final ReviewRepository reviewRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final PointsService pointsService;

    public ReviewWorkflowService(AnswerRepository answerRepository,
                                ReviewRepository reviewRepository,
                                TaskRepository taskRepository,
                                ProjectRepository projectRepository,
                                PointsService pointsService) {
        this.answerRepository = answerRepository;
        this.reviewRepository = reviewRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.pointsService = pointsService;
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

    private DecisionResult decide(UUID answerId, UUID reviewerId, ReviewDecision decision, String comment) {
        AnswerEntity answer = answerRepository.findByIdWithTask(answerId)
                .orElseThrow(() -> new ApiNotFoundException("Answer not found: " + answerId));

        TaskEntity task = answer.getTask();
        if (task == null) {
            // Fallback (should not happen with fetch join)
            task = taskRepository.findById(answer.getTaskId())
                    .orElseThrow(() -> new ApiNotFoundException("Task not found: " + answer.getTaskId()));
        }

        if (answer.getUserId().equals(reviewerId)) {
            throw new ApiForbiddenException("Reviewer cannot review own answer");
        }

        if (answer.getStatus() != AnswerStatus.SUBMITTED) {
            throw new ApiConflictException("Answer must be SUBMITTED to review (status=" + answer.getStatus() + ")");
        }

        if (task.getStatus() != TaskStatus.IN_REVIEW) {
            throw new ApiConflictException("Task must be IN_REVIEW to review answers (status=" + task.getStatus() + ")");
        }

        if (reviewRepository.existsByAnswerIdAndReviewerId(answerId, reviewerId)) {
            throw new ApiConflictException("Reviewer already reviewed this answer");
        }

        ReviewEntity review = new ReviewEntity();
        review.setAnswerId(answerId);
        review.setReviewerId(reviewerId);
        review.setDecision(decision);
        review.setComment(comment);
        reviewRepository.save(review);

        UUID projectId = task.getProjectId();
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiNotFoundException("Project not found: " + projectId));

        int required = (project.getReviewersCount() == null ? 1 : project.getReviewersCount());
        if (required < 1) required = 1;

        if (decision == ReviewDecision.REJECTED) {
            // Hard reject: answer rejected, task returns to pool
            answer.setStatus(AnswerStatus.REJECTED);
            answerRepository.save(answer);

            task.setStatus(TaskStatus.NEW);
            task.setLockedByUserId(null);
            task.setLockedAt(null);
            taskRepository.save(task);

            return new DecisionResult(answer, task, 0, 0L, required);
        }

        long approvals = reviewRepository.countByAnswerIdAndDecision(answerId, ReviewDecision.APPROVED);

        int awarded = 0;
        if (approvals >= required) {
            // Final approval path
            answer.setStatus(AnswerStatus.APPROVED);
            answerRepository.save(answer);

            task.setStatus(TaskStatus.APPROVED);
            taskRepository.save(task);

            int reward = (project.getRewardPoints() == null ? 0 : project.getRewardPoints());
            awarded = pointsService.awardTaskApprovedOnce(answer.getUserId(), task.getId(), reward);
        }

        return new DecisionResult(answer, task, awarded, approvals, required);
    }

    public record DecisionResult(AnswerEntity answer, TaskEntity task, int pointsAwarded, long approvals, int requiredApprovals) {}
}
