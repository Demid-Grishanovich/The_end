package com.datacrowd.core.dto;

import com.datacrowd.core.entity.DataType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateProjectRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    @NotNull
    private DataType dataType = DataType.TEXT;

    private Integer reviewersCount;
    private Integer rewardPoints;

    // НОВОЕ: минимальное время ответа (защита от ботов)
    @Min(0)
    @Max(300)
    private Integer minAnswerSeconds = 3;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public DataType getDataType() { return dataType; }
    public void setDataType(DataType dataType) { this.dataType = dataType; }

    public Integer getReviewersCount() { return reviewersCount; }
    public void setReviewersCount(Integer reviewersCount) { this.reviewersCount = reviewersCount; }

    public Integer getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(Integer rewardPoints) { this.rewardPoints = rewardPoints; }

    public Integer getMinAnswerSeconds() { return minAnswerSeconds; }
    public void setMinAnswerSeconds(Integer minAnswerSeconds) { this.minAnswerSeconds = minAnswerSeconds; }
}