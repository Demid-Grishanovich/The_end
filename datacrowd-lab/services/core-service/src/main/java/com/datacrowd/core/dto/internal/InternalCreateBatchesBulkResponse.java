package com.datacrowd.core.dto.internal;

import java.util.List;
import java.util.UUID;

public class InternalCreateBatchesBulkResponse {
    public List<UUID> batchIds;

    public InternalCreateBatchesBulkResponse(List<UUID> batchIds) {
        this.batchIds = batchIds;
    }
}
