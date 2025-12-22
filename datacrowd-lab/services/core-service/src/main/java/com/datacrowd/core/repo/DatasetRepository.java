package com.datacrowd.core.repo;

import com.datacrowd.core.entity.DatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatasetRepository extends JpaRepository<DatasetEntity, UUID> {
    List<DatasetEntity> findAllByProjectId(UUID projectId);
    Optional<DatasetEntity> findByIdAndProjectId(UUID id, UUID projectId);
}
