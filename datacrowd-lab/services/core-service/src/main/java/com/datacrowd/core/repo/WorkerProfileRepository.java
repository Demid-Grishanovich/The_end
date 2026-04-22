package com.datacrowd.core.repo;

import com.datacrowd.core.entity.WorkerProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkerProfileRepository extends JpaRepository<WorkerProfileEntity, UUID> {}