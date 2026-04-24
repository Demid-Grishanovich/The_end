package com.datacrowd.core.repo;

import com.datacrowd.core.entity.AnswerEntity;
import com.datacrowd.core.entity.AnswerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<AnswerEntity, UUID> {

    @Query("""
        select a
        from AnswerEntity a
        join fetch a.task t
        where a.status = com.datacrowd.core.entity.AnswerStatus.SUBMITTED
          and t.status = com.datacrowd.core.entity.TaskStatus.IN_REVIEW
          and a.userId <> :reviewerId
          and not exists (
            select r
            from ReviewEntity r
            where r.answerId = a.id
              and r.reviewerId = :reviewerId
          )
        order by a.createdAt asc
    """)
    Optional<AnswerEntity> findNextForReview(@Param("reviewerId") UUID reviewerId);

    @Query("""
        select a
        from AnswerEntity a
        join fetch a.task t
        where a.id = :id
    """)
    Optional<AnswerEntity> findByIdWithTask(@Param("id") UUID id);

    @Query("""
        select a
        from AnswerEntity a
        join fetch a.task t
        where t.projectId = :projectId
          and t.datasetId = :datasetId
          and a.status = :status
        order by a.createdAt asc
    """)
    List<AnswerEntity> findByProjectDatasetAndStatus(
            @Param("projectId") UUID projectId,
            @Param("datasetId") UUID datasetId,
            @Param("status") AnswerStatus status
    );

    // НОВОЕ: подсчёт ответов воркера по статусу (для Worker Stats)
    long countByUserIdAndStatus(UUID userId, AnswerStatus status);
}