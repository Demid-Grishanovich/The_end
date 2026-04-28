package com.datacrowd.core.service;

import com.datacrowd.core.config.CacheConfig;
import com.datacrowd.core.dto.WorkerStatsResponse;
import com.datacrowd.core.entity.AnswerStatus;
import com.datacrowd.core.entity.WorkerProfileEntity;
import com.datacrowd.core.repo.AnswerRepository;
import com.datacrowd.core.repo.PointsLedgerRepository;
import com.datacrowd.core.repo.WorkerProfileRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WorkerStatsService {

    private final WorkerProfileRepository workerProfileRepository;
    private final AnswerRepository        answerRepository;
    private final PointsLedgerRepository  pointsLedgerRepository;

    public WorkerStatsService(WorkerProfileRepository workerProfileRepository,
                              AnswerRepository answerRepository,
                              PointsLedgerRepository pointsLedgerRepository) {
        this.workerProfileRepository = workerProfileRepository;
        this.answerRepository        = answerRepository;
        this.pointsLedgerRepository  = pointsLedgerRepository;
    }

    @Cacheable(value = CacheConfig.WORKER_STATS, key = "#workerId")
    @Transactional(readOnly = true)
    public WorkerStatsResponse getStats(UUID workerId) {
        WorkerProfileEntity profile = workerProfileRepository.findById(workerId)
                .orElseGet(() -> {
                    WorkerProfileEntity p = new WorkerProfileEntity(workerId);
                    p.setTrustScore(100);
                    return p;
                });

        int trustScore      = profile.getTrustScore() != null ? profile.getTrustScore() : 100;
        long completedTasks = answerRepository.countByUserIdAndStatus(workerId, AnswerStatus.APPROVED);
        long rejectedTasks  = answerRepository.countByUserIdAndStatus(workerId, AnswerStatus.REJECTED);
        int  totalPoints    = pointsLedgerRepository.sumPointsByUserId(workerId);

        return new WorkerStatsResponse(
                trustScore,
                (int) completedTasks,
                (int) rejectedTasks,
                totalPoints
        );
    }

    @CacheEvict(value = CacheConfig.WORKER_STATS, key = "#workerId")
    public void evictStats(UUID workerId) {
        // evict only — annotation does the work
    }
}