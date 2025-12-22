package com.datacrowd.core.dto;

import com.datacrowd.core.entity.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public class ProjectResponse {
    public UUID id;
    public UUID ownerUserId;
    public String name;
    public String description;
    public String dataType;
    public ProjectStatus status;
    public Integer reviewersCount;
    public Integer rewardPoints;
    public String billingStatus;
    public Integer taskQuota;
    public Instant createdAt;
    public Instant updatedAt;
}
