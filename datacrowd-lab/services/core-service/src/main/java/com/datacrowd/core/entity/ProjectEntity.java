package com.datacrowd.core.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID ownerUserId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private DataType dataType = DataType.TEXT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.NEW;

    private Integer reviewersCount;
    private Integer rewardPoints;

    // ИЗМЕНЕНО: было String, теперь BillingStatus enum
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", nullable = false, length = 20)
    private BillingStatus billingStatus = BillingStatus.UNPAID;

    private Integer taskQuota = 0;

    // НОВОЕ: минимальное время ответа для time-tracking
    @Column(name = "min_answer_seconds", nullable = false)
    private int minAnswerSeconds = 3;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    // getters / setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public DataType getDataType() { return dataType; }
    public void setDataType(DataType dataType) { this.dataType = dataType; }

    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }

    public Integer getReviewersCount() { return reviewersCount; }
    public void setReviewersCount(Integer reviewersCount) { this.reviewersCount = reviewersCount; }

    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints; }

    public BillingStatus getBillingStatus() { return billingStatus; }
    public void setBillingStatus(BillingStatus billingStatus) { this.billingStatus = billingStatus; }

    public Integer getTaskQuota() { return taskQuota; }
    public void setTaskQuota(Integer taskQuota) { this.taskQuota = taskQuota; }

    public int getMinAnswerSeconds() { return minAnswerSeconds; }
    public void setMinAnswerSeconds(int minAnswerSeconds) { this.minAnswerSeconds = minAnswerSeconds; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}