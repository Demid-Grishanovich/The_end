package com.datacrowd.core.dto.internal;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class InternalCreateBatchRequest {
    @NotNull
    public UUID datasetId;

    public String status; // NEW/READY
}
