package com.datacrowd.core.repo;

import com.datacrowd.core.entity.PointsLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PointsLedgerRepository extends JpaRepository<PointsLedgerEntity, UUID> {
    boolean existsByUserIdAndTaskIdAndReason(UUID userId, UUID taskId, String reason);
}
