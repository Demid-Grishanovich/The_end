package com.datacrowd.core.api;

import com.datacrowd.core.dto.ReviewDecisionRequest;
import com.datacrowd.core.dto.ReviewDecisionResponse;
import com.datacrowd.core.dto.ReviewNextResponse;
import com.datacrowd.core.entity.AnswerEntity;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.ReviewWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/core/reviews")
public class ReviewsController {

    private final ReviewWorkflowService reviewWorkflowService;

    public ReviewsController(ReviewWorkflowService reviewWorkflowService) {
        this.reviewWorkflowService = reviewWorkflowService;
    }

    @GetMapping("/next")
    public ResponseEntity<ReviewNextResponse> next() {
        UUID reviewerId = AuthContext.getUserIdOrThrow();
        return reviewWorkflowService.nextForReview(reviewerId)
                .map(a -> ResponseEntity.ok(toNextResponse(a)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{answerId}/approve")
    public ReviewDecisionResponse approve(@PathVariable UUID answerId, @Valid @RequestBody(required = false) ReviewDecisionRequest req) {
        UUID reviewerId = AuthContext.getUserIdOrThrow();
        String comment = (req == null ? null : req.comment);
        var res = reviewWorkflowService.approve(answerId, reviewerId, comment);
        return toDecisionResponse(res);
    }

    @PostMapping("/{answerId}/reject")
    public ReviewDecisionResponse reject(@PathVariable UUID answerId, @Valid @RequestBody(required = false) ReviewDecisionRequest req) {
        UUID reviewerId = AuthContext.getUserIdOrThrow();
        String comment = (req == null ? null : req.comment);
        var res = reviewWorkflowService.reject(answerId, reviewerId, comment);
        return toDecisionResponse(res);
    }

    private ReviewNextResponse toNextResponse(AnswerEntity a) {
        ReviewNextResponse r = new ReviewNextResponse();
        r.answerId = a.getId();
        r.taskId = a.getTaskId();
        r.workerUserId = a.getUserId();
        r.answerContent = a.getContent();
        r.taskPayloadJson = (a.getTask() != null ? a.getTask().getPayloadJson() : null);
        return r;
    }

    private ReviewDecisionResponse toDecisionResponse(ReviewWorkflowService.DecisionResult res) {
        ReviewDecisionResponse out = new ReviewDecisionResponse();
        out.answerId = res.answer().getId();
        out.taskId = res.task().getId();
        out.answerStatus = res.answer().getStatus().name();
        out.taskStatus = res.task().getStatus().name();
        out.pointsAwarded = res.pointsAwarded();
        out.approvals = res.approvals();
        out.requiredApprovals = res.requiredApprovals();
        return out;
    }
}
