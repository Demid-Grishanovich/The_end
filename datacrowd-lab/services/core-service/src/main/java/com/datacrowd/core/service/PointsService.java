package com.datacrowd.core.service;

import com.datacrowd.core.entity.PointsLedgerEntity;
import com.datacrowd.core.repo.PointsLedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PointsService {

    public static final String REASON_TASK_APPROVED = "TASK_APPROVED";

    private final PointsLedgerRepository pointsLedgerRepository;

    public PointsService(PointsLedgerRepository pointsLedgerRepository) {
        this.pointsLedgerRepository = pointsLedgerRepository;
    }

    /**
     * Idempotent points award: only one record per (userId, taskId, reason).
     */
    @Transactional
    public int awardTaskApprovedOnce(UUID userId, UUID taskId, int points) {
        if (points <= 0) return 0;
        if (pointsLedgerRepository.existsByUserIdAndTaskIdAndReason(userId, taskId, REASON_TASK_APPROVED)) {
            return 0;
        }
        PointsLedgerEntity e = new PointsLedgerEntity();
        e.setUserId(userId);
        e.setTaskId(taskId);
        e.setPoints(points);
        e.setReason(REASON_TASK_APPROVED);
        pointsLedgerRepository.save(e);
        return points;
    }
}
