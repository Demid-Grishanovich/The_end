package com.datacrowd.core.service;

import com.datacrowd.core.entity.AuditLogEntity;
import com.datacrowd.core.repo.AuditLogRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void log(UUID actorId, String action,
                    String entityType, UUID entityId,
                    String details, String ipAddress) {
        try {
            auditLogRepository.save(new AuditLogEntity(
                    actorId, action, entityType, entityId, details, ipAddress
            ));
        } catch (Exception e) {
        }
    }

    @Async
    public void log(UUID actorId, String action,
                    String entityType, UUID entityId) {
        log(actorId, action, entityType, entityId, null, null);
    }
}