package com.datacrowd.core.repo;

import com.datacrowd.core.entity.FailedItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FailedItemRepository extends JpaRepository<FailedItemEntity, UUID> {

    List<FailedItemEntity> findAllByDatasetId(UUID datasetId);

    @Query("select count(f) from FailedItemEntity f where f.datasetId = :datasetId")
    long countByDatasetId(@Param("datasetId") UUID datasetId);
}