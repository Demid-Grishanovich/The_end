package com.datacrowd.core.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(columnDefinition = "text")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLogEntity() {}

    public AuditLogEntity(UUID actorId, String action,
                          String entityType, UUID entityId,
                          String details, String ipAddress) {
        this.actorId    = actorId;
        this.action     = action;
        this.entityType = entityType;
        this.entityId   = entityId;
        this.details    = details;
        this.ipAddress  = ipAddress;
    }

    // Константы действий — не используем магические строки
    public static final String PROJECT_CREATED   = "PROJECT_CREATED";
    public static final String DATASET_UPLOADED  = "DATASET_UPLOADED";
    public static final String TASKS_GENERATED   = "TASKS_GENERATED";
    public static final String TASK_LOCKED       = "TASK_LOCKED";
    public static final String TASK_SUBMITTED    = "TASK_SUBMITTED";
    public static final String ANSWER_APPROVED   = "ANSWER_APPROVED";
    public static final String ANSWER_REJECTED   = "ANSWER_REJECTED";
    public static final String EXPORT_CREATED    = "EXPORT_CREATED";
    public static final String ROLE_CHANGED      = "ROLE_CHANGED";
    public static final String BOT_DETECTED      = "BOT_DETECTED";
    public static final String HONEYPOT_FAILED   = "HONEYPOT_FAILED";

    public UUID    getId()          { return id; }
    public UUID    getActorId()     { return actorId; }
    public String  getAction()      { return action; }
    public String  getEntityType()  { return entityType; }
    public UUID    getEntityId()    { return entityId; }
    public String  getDetails()     { return details; }
    public String  getIpAddress()   { return ipAddress; }
    public Instant getCreatedAt()   { return createdAt; }
}