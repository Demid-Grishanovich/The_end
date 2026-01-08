package com.datacrowd.core.dto;

import java.util.UUID;

public class SubmitTaskResponse {
    public UUID taskId;
    public UUID answerId;
    public String taskStatus;
    public Integer pointsAwarded;
}
