package com.datacrowd.core.repo;

import com.datacrowd.core.entity.PointsLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PointsLedgerRepository extends JpaRepository<PointsLedgerEntity, UUID> {

    boolean existsByUserIdAndTaskIdAndReason(UUID userId, UUID taskId, String reason);

    @Query("select coalesce(sum(p.points), 0) from PointsLedgerEntity p where p.userId = :userId")
    int sumPointsByUserId(@Param("userId") UUID userId);
}