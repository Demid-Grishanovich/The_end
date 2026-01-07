package com.datacrowd.core.dto.internal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class InternalCreateBatchesBulkRequest {

    public static class BatchItem {
        @NotNull public UUID datasetId;
        public String status; // NEW/READY/...
        public Integer totalTasks; // optional (defaults 0)
    }

    @NotEmpty
    public List<BatchItem> batches;
}
