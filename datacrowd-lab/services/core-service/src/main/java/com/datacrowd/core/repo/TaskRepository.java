package com.datacrowd.core.repo;

import com.datacrowd.core.entity.BatchStatus;
import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    List<TaskEntity> findAllByBatchId(UUID batchId);

    Optional<TaskEntity> findFirstByLockedByUserIdAndStatus(UUID lockedByUserId, TaskStatus status);

    /**
     * Picks the oldest available task from any READY batch.
     *
     * Criteria:
     * - task.status = NEW
     * - task.lockedByUserId is null
     * - batch.status = READY
     */
    @Query("""
    select t
    from TaskEntity t
    join t.batch b
    where t.status = com.datacrowd.core.entity.TaskStatus.NEW
      and t.lockedByUserId is null
      and b.status = com.datacrowd.core.entity.BatchStatus.READY
    order by t.createdAt asc
""")
    List<TaskEntity> findNextAvailableTasks(org.springframework.data.domain.Pageable pageable);

    /**
     * Atomic lock: locks task only if it is currently unlocked and in NEW state.
     * Returns number of updated rows (0 or 1).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity t
               set t.status = com.datacrowd.core.entity.TaskStatus.LOCKED,
                   t.lockedByUserId = :workerUserId,
                   t.lockedAt = :lockedAt
             where t.id = :taskId
               and t.status = com.datacrowd.core.entity.TaskStatus.NEW
               and t.lockedByUserId is null
            """)
    int lockIfAvailable(@Param("taskId") UUID taskId,
                        @Param("workerUserId") UUID workerUserId,
                        @Param("lockedAt") Instant lockedAt);

    /**
     * Atomic unlock: unlocks task only if it is currently locked by given worker and in LOCKED state.
     * Returns number of updated rows (0 or 1).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update TaskEntity t
               set t.status = com.datacrowd.core.entity.TaskStatus.NEW,
                   t.lockedByUserId = null,
                   t.lockedAt = null
             where t.id = :taskId
               and t.status = com.datacrowd.core.entity.TaskStatus.LOCKED
               and t.lockedByUserId = :workerUserId
            """)
    int unlockOwned(@Param("taskId") UUID taskId,
                    @Param("workerUserId") UUID workerUserId);

    /**
     * For idempotency: in case a worker tries to lock again a task already locked by them.
     */
    @Query("""
            select t
              from TaskEntity t
             where t.id = :taskId
               and t.lockedByUserId = :workerUserId
               and t.status = com.datacrowd.core.entity.TaskStatus.LOCKED
            """)
    Optional<TaskEntity> findLockedByMe(@Param("taskId") UUID taskId,
                                        @Param("workerUserId") UUID workerUserId);

    /**
     * How many tasks in a batch are still "active" (not approved). Useful for progress / batch completion checks.
     * (Not required for compilation, but handy and safe.)
     */
    @Query("""
            select count(t)
              from TaskEntity t
             where t.batchId = :batchId
               and t.status <> com.datacrowd.core.entity.TaskStatus.APPROVED
            """)
    long countNotApprovedInBatch(@Param("batchId") UUID batchId);
}
