package com.datacrowd.core.dto;

import com.datacrowd.core.entity.BillingStatus;
import com.datacrowd.core.entity.ProjectStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ProjectResponse {
    public UUID id;
    public UUID ownerUserId;
    public String title;
    public String description;
    public int rewardPoints;
    public ProjectStatus status;
    public int reviewersCount;
    public String dataType;
    public BillingStatus billingStatus;
    public int taskQuota;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
