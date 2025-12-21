package com.datacrowd.core.dto;

import com.datacrowd.core.entity.DatasetStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public class DatasetResponse {
    public UUID id;
    public UUID projectId;
    public String name;
    public String description;
    public String sourcePath;
    public DatasetStatus status;
    public int totalItems;
    public OffsetDateTime createdAt;
}
