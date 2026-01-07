package com.datacrowd.core.api.internal;

import com.datacrowd.core.dto.internal.InternalCreateBatchesBulkRequest;
import com.datacrowd.core.dto.internal.InternalCreateBatchesBulkResponse;
import com.datacrowd.core.entity.BatchStatus;
import com.datacrowd.core.entity.TaskBatchEntity;
import com.datacrowd.core.repo.TaskBatchRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/task-batches")
public class InternalTaskBatchesController {

    private final TaskBatchRepository taskBatchRepository;

    public InternalTaskBatchesController(TaskBatchRepository taskBatchRepository) {
        this.taskBatchRepository = taskBatchRepository;
    }

    @PostMapping("/bulk")
    public InternalCreateBatchesBulkResponse bulkCreate(@Valid @RequestBody InternalCreateBatchesBulkRequest req) {
        List<TaskBatchEntity> toSave = new ArrayList<>();
        for (var item : req.batches) {
            TaskBatchEntity b = new TaskBatchEntity();
            b.setDatasetId(item.datasetId);

            if (item.totalTasks != null && item.totalTasks >= 0) {
                b.setTotalTasks(item.totalTasks);
            }

            if (item.status != null && !item.status.isBlank()) {
                try {
                    b.setStatus(BatchStatus.valueOf(item.status));
                } catch (Exception ignore) {
                    b.setStatus(BatchStatus.NEW);
                }
            } else {
                b.setStatus(BatchStatus.NEW);
            }

            toSave.add(b);
        }

        List<TaskBatchEntity> saved = taskBatchRepository.saveAll(toSave);

        List<UUID> ids = saved.stream().map(TaskBatchEntity::getId).toList();
        return new InternalCreateBatchesBulkResponse(ids);
    }
}
