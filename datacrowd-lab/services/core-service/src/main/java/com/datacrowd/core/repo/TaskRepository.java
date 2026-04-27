package com.datacrowd.core.repo;

import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    List<TaskEntity> findAllByBatchId(UUID batchId);

    Optional<TaskEntity> findFirstByLockedByUserIdAndStatus(UUID lockedByUserId, TaskStatus status);

    @Query("""
        select t
        from TaskEntity t
        where t.status = com.datacrowd.core.entity.TaskStatus.NEW
          and t.lockedByUserId is null
        order by t.createdAt asc
    """)
    List<TaskEntity> findNextAvailableTasks(org.springframework.data.domain.Pageable pageable);

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

    @Query("""
        select t
          from TaskEntity t
         where t.id = :taskId
           and t.lockedByUserId = :workerUserId
           and t.status = com.datacrowd.core.entity.TaskStatus.LOCKED
    """)
    Optional<TaskEntity> findLockedByMe(@Param("taskId") UUID taskId,
                                        @Param("workerUserId") UUID workerUserId);

    @Query("""
        select count(t)
          from TaskEntity t
         where t.batchId = :batchId
           and t.status <> com.datacrowd.core.entity.TaskStatus.APPROVED
    """)
    long countNotApprovedInBatch(@Param("batchId") UUID batchId);

    // НОВОЕ: подсчёт всех задач проекта
    @Query("select count(t) from TaskEntity t where t.projectId = :projectId")
    long countAllByProject(@Param("projectId") UUID projectId);

    // НОВОЕ: подсчёт завершённых (APPROVED) задач проекта
    @Query("""
        select count(t) from TaskEntity t
        where t.projectId = :projectId
          and t.status = com.datacrowd.core.entity.TaskStatus.APPROVED
    """)
    long countApprovedByProject(@Param("projectId") UUID projectId);

    @Query("""
        select t from TaskEntity t
        where t.projectId = :projectId
          and t.status = com.datacrowd.core.entity.TaskStatus.NEW
          and t.lockedByUserId is null
        order by t.createdAt asc
    """)
    List<TaskEntity> findNextAvailableByProject(
            @Param("projectId") UUID projectId,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
        select distinct t.projectId from TaskEntity t
        where t.status = com.datacrowd.core.entity.TaskStatus.NEW
          and t.lockedByUserId is null
    """)
    List<UUID> findProjectIdsWithAvailableTasks();

    @Query("""
        select count(t) from TaskEntity t
        where t.projectId = :projectId
          and t.status = com.datacrowd.core.entity.TaskStatus.NEW
          and t.lockedByUserId is null
    """)
    long countAvailableByProject(@Param("projectId") UUID projectId);
}