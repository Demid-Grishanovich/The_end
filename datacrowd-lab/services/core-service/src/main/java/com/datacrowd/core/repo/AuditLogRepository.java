package com.datacrowd.core.repo;

import com.datacrowd.core.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    List<AuditLogEntity> findTop50ByActorIdOrderByCreatedAtDesc(UUID actorId);

    List<AuditLogEntity> findTop50ByEntityIdOrderByCreatedAtDesc(UUID entityId);
}