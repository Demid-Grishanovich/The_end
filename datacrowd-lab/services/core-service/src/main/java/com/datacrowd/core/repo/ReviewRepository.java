package com.datacrowd.core.repo;

import com.datacrowd.core.entity.ReviewDecision;
import com.datacrowd.core.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {
    boolean existsByAnswerIdAndReviewerId(UUID answerId, UUID reviewerId);
    long countByAnswerIdAndDecision(UUID answerId, ReviewDecision decision);
}
