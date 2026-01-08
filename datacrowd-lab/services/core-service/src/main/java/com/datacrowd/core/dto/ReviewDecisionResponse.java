package com.datacrowd.core.dto;

import java.util.UUID;

public class ReviewDecisionResponse {
    public UUID answerId;
    public UUID taskId;
    public String answerStatus;
    public String taskStatus;
    public Integer pointsAwarded;
    public Long approvals;
    public Integer requiredApprovals;
}
