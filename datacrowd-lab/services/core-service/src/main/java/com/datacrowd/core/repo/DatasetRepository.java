package com.datacrowd.core.repo;

import com.datacrowd.core.entity.DatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DatasetRepository extends JpaRepository<DatasetEntity, UUID> {
    List<DatasetEntity> findAllByProjectId(UUID projectId);
}
