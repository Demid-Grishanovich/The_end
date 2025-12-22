package com.datacrowd.core.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class GenerateTasksRequest {

    @NotNull
    @Min(1)
    public Integer batchSize = 20;

    @NotNull
    @Min(1)
    public Integer reviewersCount = 1;

    @NotNull
    @Min(0)
    public Integer rewardPoints = 0;
}
