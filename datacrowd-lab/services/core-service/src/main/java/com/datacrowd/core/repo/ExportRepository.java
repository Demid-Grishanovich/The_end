package com.datacrowd.core.repo;

import com.datacrowd.core.entity.ExportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExportRepository extends JpaRepository<ExportEntity, UUID> {
}
