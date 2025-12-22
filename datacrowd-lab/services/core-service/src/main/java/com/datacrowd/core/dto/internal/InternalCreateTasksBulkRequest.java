package com.datacrowd.core.dto.internal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class InternalCreateTasksBulkRequest {

    public static class TaskItem {
        @NotNull public UUID batchId;
        @NotNull public String payloadJson;
        public String status; // NEW/READY
    }

    @NotEmpty
    public List<TaskItem> tasks;
}
