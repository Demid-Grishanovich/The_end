package com.datacrowd.core.dto;

import java.time.Instant;
import java.util.UUID;

public class TaskResponse {
    public UUID id;
    public UUID projectId;
    public UUID datasetId;
    public UUID batchId;
    public String payloadJson;
    public String status;
    public UUID lockedByUserId;
    public Instant lockedAt;
    public Instant createdAt;
    public String assetUrl;
}
