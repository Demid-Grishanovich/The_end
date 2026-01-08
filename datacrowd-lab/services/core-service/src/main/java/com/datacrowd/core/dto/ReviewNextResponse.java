package com.datacrowd.core.dto;

import java.util.UUID;

public class ReviewNextResponse {
    public UUID answerId;
    public UUID taskId;
    public UUID workerUserId;
    public String answerContent;
    public String taskPayloadJson;
}
