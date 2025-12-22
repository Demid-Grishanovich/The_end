package com.datacrowd.core.dto;

import com.datacrowd.core.entity.DatasetStatus;

import java.time.Instant;
import java.util.UUID;

public class DatasetResponse {
    public UUID id;
    public UUID projectId;
    public String sourcePath;
    public DatasetStatus status;
    public Integer totalItems;
    public Instant createdAt;
}
