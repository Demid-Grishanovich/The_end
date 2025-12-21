package com.datacrowd.core.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Column(name = "reviewers_count", nullable = false)
    private int reviewersCount = 1;

    @Column(name = "data_type", nullable = false, length = 50)
    private String dataType = "GENERIC";

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", nullable = false, length = 50)
    private BillingStatus billingStatus = BillingStatus.UNPAID;

    @Column(name = "task_quota", nullable = false)
    private int taskQuota = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    // getters/setters
    public UUID getId() { return id; }

    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(int rewardPoints) { this.rewardPoints = rewardPoints; }

    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }

    public int getReviewersCount() { return reviewersCount; }
    public void setReviewersCount(int reviewersCount) { this.reviewersCount = reviewersCount; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public BillingStatus getBillingStatus() { return billingStatus; }
    public void setBillingStatus(BillingStatus billingStatus) { this.billingStatus = billingStatus; }

    public int getTaskQuota() { return taskQuota; }
    public void setTaskQuota(int taskQuota) { this.taskQuota = taskQuota; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
