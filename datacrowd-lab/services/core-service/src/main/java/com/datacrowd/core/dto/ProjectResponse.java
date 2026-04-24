package com.datacrowd.core.dto;

import com.datacrowd.core.entity.BillingStatus;
import com.datacrowd.core.entity.DataType;
import com.datacrowd.core.entity.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public class ProjectResponse {
    public UUID          id;
    public UUID          ownerUserId;
    public String        name;
    public String        description;
    public DataType      dataType;
    public ProjectStatus status;
    public Integer       reviewersCount;
    public Integer       rewardPoints;
    public BillingStatus billingStatus;   // ИЗМЕНЕНО: было String
    public Integer       taskQuota;
    public int           minAnswerSeconds; // НОВОЕ
    public Instant       createdAt;
    public Instant       updatedAt;

    // Поля прогресса для прогресс-бара
    public Long    totalTasks;
    public Long    completedTasks;
    public Integer progress;
}