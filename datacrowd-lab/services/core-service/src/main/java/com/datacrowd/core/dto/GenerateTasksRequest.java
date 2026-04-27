package com.datacrowd.core.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class GenerateTasksRequest {


    public UUID projectId;
    public Integer reviewersCount = null;
    public Integer rewardPoints   = null;
}

