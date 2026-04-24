package com.datacrowd.core.repo;

import com.datacrowd.core.entity.ProjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    List<ProjectEntity> findAllByOwnerUserId(UUID ownerUserId);

    Page<ProjectEntity> findAllByOwnerUserId(UUID ownerUserId, Pageable pageable);
}