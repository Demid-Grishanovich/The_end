package com.datacrowd.core.api.internal;

import com.datacrowd.core.dto.internal.InternalCreateTasksBulkRequest;
import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.entity.TaskBatchEntity;
import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.entity.TaskStatus;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.TaskBatchRepository;
import com.datacrowd.core.repo.TaskRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/tasks")
public class InternalTasksController {

    private final TaskRepository taskRepository;
    private final TaskBatchRepository taskBatchRepository;
    private final DatasetRepository datasetRepository;

    public InternalTasksController(
            TaskRepository taskRepository,
            TaskBatchRepository taskBatchRepository,
            DatasetRepository datasetRepository
    ) {
        this.taskRepository = taskRepository;
        this.taskBatchRepository = taskBatchRepository;
        this.datasetRepository = datasetRepository;
    }

    @PostMapping("/bulk")
    public String bulkCreate(@Valid @RequestBody InternalCreateTasksBulkRequest req) {

        if (req.tasks == null || req.tasks.isEmpty()) {
            return "ok";
        }

        // 1) Собираем batchIds и грузим батчи пачкой
        Set<UUID> batchIds = req.tasks.stream()
                .map(t -> t.batchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, TaskBatchEntity> batches = taskBatchRepository.findAllById(batchIds).stream()
                .collect(Collectors.toMap(TaskBatchEntity::getId, b -> b));

        // 2) Собираем datasetIds и грузим datasets пачкой
        Set<UUID> datasetIds = batches.values().stream()
                .map(TaskBatchEntity::getDatasetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, DatasetEntity> datasets = datasetRepository.findAllById(datasetIds).stream()
                .collect(Collectors.toMap(DatasetEntity::getId, d -> d));

        // 3) Собираем tasks
        List<TaskEntity> toSave = new ArrayList<>();
        int i = 1;

        for (var item : req.tasks) {
            TaskBatchEntity batch = batches.get(item.batchId);
            if (batch == null) {
                // если runner прислал batchId которого нет — пропускаем
                continue;
            }

            DatasetEntity dataset = datasets.get(batch.getDatasetId());
            if (dataset == null) {
                continue;
            }

            TaskEntity t = new TaskEntity();

            // ✅ обязательные поля по схеме
            t.setBatchId(item.batchId);
            t.setDatasetId(dataset.getId());
            t.setProjectId(dataset.getProjectId());
            t.setTitle("Generated task #" + (i++));
            t.setPayloadJson(item.payloadJson);

            // статус — только если валидный enum
            if (item.status != null && !item.status.isBlank()) {
                try {
                    t.setStatus(TaskStatus.valueOf(item.status));
                } catch (Exception ignore) {
                    t.setStatus(TaskStatus.NEW);
                }
            } else {
                t.setStatus(TaskStatus.NEW);
            }

            toSave.add(t);
        }

        taskRepository.saveAll(toSave);
        return "ok";
    }
}
