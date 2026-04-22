package com.datacrowd.core.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "worker_profiles")
public class WorkerProfileEntity {
    @Id
    @Column(name = "worker_id")
    private UUID workerId;

    @Column(name = "trust_score")
    private Integer trustScore = 100;

    public WorkerProfileEntity() {}
    public WorkerProfileEntity(UUID workerId) { this.workerId = workerId; }

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }
    public Integer getTrustScore() { return trustScore; }
    public void setTrustScore(Integer trustScore) { this.trustScore = trustScore; }
}