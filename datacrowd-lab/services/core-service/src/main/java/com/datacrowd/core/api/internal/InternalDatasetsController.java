package com.datacrowd.core.api.internal;

import com.datacrowd.core.entity.DatasetStatus;
import com.datacrowd.core.entity.FailedItemEntity;
import com.datacrowd.core.repo.FailedItemRepository;
import com.datacrowd.core.service.DatasetService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/datasets")
public class InternalDatasetsController {

    private final DatasetService       datasetService;
    private final FailedItemRepository failedItemRepository;

    public InternalDatasetsController(DatasetService datasetService,
                                      FailedItemRepository failedItemRepository) {
        this.datasetService       = datasetService;
        this.failedItemRepository = failedItemRepository;
    }

    /**
     * Обновить статус датасета (вызывается из Go runner).
     * PATCH /internal/datasets/{id}/status?status=READY
     */
    @PatchMapping("/{id}/status")
    public String updateStatus(@PathVariable UUID id,
                               @RequestParam String status) {
        DatasetStatus s = DatasetStatus.valueOf(status.toUpperCase());
        datasetService.updateDatasetStatusInternal(id, s);
        return "ok";
    }

    /**
     * Записать битую строку в Dead Letter Queue.
     * Вызывается из Go runner когда строка датасета не парсится.
     * POST /internal/datasets/{id}/failed-items
     */
    @PostMapping("/{datasetId}/failed-items")
    public String addFailedItem(
            @PathVariable UUID datasetId,
            @RequestBody FailedItemRequest req) {

        FailedItemEntity item = new FailedItemEntity(
                datasetId,
                req.lineNumber(),
                req.rawContent(),
                req.errorMsg()
        );
        failedItemRepository.save(item);
        return "ok";
    }

    public record FailedItemRequest(
            int    lineNumber,
            String rawContent,
            String errorMsg
    ) {}
}