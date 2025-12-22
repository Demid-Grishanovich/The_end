package com.datacrowd.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_batches")
public class TaskBatchEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "dataset_id", nullable = false, columnDefinition = "uuid")
    private UUID datasetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BatchStatus status = BatchStatus.NEW;

    @Column(name = "claimed_by_user_id", columnDefinition = "uuid")
    private UUID claimedByUserId;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        if (status == null) status = BatchStatus.NEW;
    }

    // getters/setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }

    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }

    public UUID getClaimedByUserId() { return claimedByUserId; }
    public void setClaimedByUserId(UUID claimedByUserId) { this.claimedByUserId = claimedByUserId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
