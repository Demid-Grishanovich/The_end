package com.datacrowd.core.dto;

import jakarta.validation.constraints.*;

public class CreateProjectRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    private String description;

    @Min(0)
    @Max(1000000)
    private int rewardPoints = 0;

    @Min(1)
    @Max(10)
    private int reviewersCount = 1;

    @NotBlank
    @Size(max = 50)
    private String dataType = "GENERIC";

    @Min(0)
    @Max(1000000)
    private int taskQuota = 0;

    // getters/setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(int rewardPoints) { this.rewardPoints = rewardPoints; }

    public int getReviewersCount() { return reviewersCount; }
    public void setReviewersCount(int reviewersCount) { this.reviewersCount = reviewersCount; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public int getTaskQuota() { return taskQuota; }
    public void setTaskQuota(int taskQuota) { this.taskQuota = taskQuota; }
}
