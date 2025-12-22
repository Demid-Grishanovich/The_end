package com.datacrowd.core.repo;

import com.datacrowd.core.entity.TaskBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskBatchRepository extends JpaRepository<TaskBatchEntity, UUID> {
    List<TaskBatchEntity> findAllByDatasetId(UUID datasetId);
}
