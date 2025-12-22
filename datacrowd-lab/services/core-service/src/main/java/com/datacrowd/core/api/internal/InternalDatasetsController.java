package com.datacrowd.core.api.internal;

import com.datacrowd.core.entity.DatasetStatus;
import com.datacrowd.core.service.DatasetService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/datasets")
public class InternalDatasetsController {

    private final DatasetService datasetService;

    public InternalDatasetsController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PatchMapping("/{datasetId}/status")
    public String updateStatus(
            @PathVariable UUID datasetId,
            @RequestParam @NotBlank String status
    ) {
        datasetService.updateDatasetStatusInternal(datasetId, DatasetStatus.valueOf(status));
        return "ok";
    }
}
