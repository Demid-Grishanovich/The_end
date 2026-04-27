package com.datacrowd.core.api.internal;

import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.entity.TaskStatus;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.TaskRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Internal API called by Go runner.
 * POST /internal/tasks/bulk  — insert all tasks in one transaction (no N+1)
 * PATCH /internal/datasets/{id}/total-items?value=N
 */
@RestController
@RequestMapping("/internal")
public class InternalTasksController {

    private final TaskRepository    taskRepository;
    private final DatasetRepository datasetRepository;

    public InternalTasksController(TaskRepository taskRepository,
                                    DatasetRepository datasetRepository) {
        this.taskRepository    = taskRepository;
        this.datasetRepository = datasetRepository;
    }

    @PostMapping("/tasks/bulk")
    @Transactional
    public BulkCreateResponse createTasksBulk(@RequestBody BulkCreateRequest req) {
        if (req.tasks() == null || req.tasks().isEmpty()) {
            return new BulkCreateResponse(0);
        }
        UUID datasetId = UUID.fromString(req.datasetId());
        UUID projectId = req.projectId() != null && !req.projectId().isBlank()
                ? UUID.fromString(req.projectId()) : null;
        if (projectId == null) {
            projectId = datasetRepository.findById(datasetId)
                    .map(d -> d.getProjectId()).orElse(null);
        }
        final UUID finalProjectId = projectId;
        List<TaskEntity> entities = new ArrayList<>(req.tasks().size());
        for (TaskItem item : req.tasks()) {
            TaskEntity task = new TaskEntity();
            task.setDatasetId(datasetId);
            task.setProjectId(finalProjectId);
            task.setPayloadJson(item.payloadJson());
            task.setStatus(item.status() != null && !item.status().isBlank()
                    ? TaskStatus.valueOf(item.status().toUpperCase()) : TaskStatus.NEW);
            entities.add(task);
        }
        List<TaskEntity> saved = taskRepository.saveAll(entities);
        return new BulkCreateResponse(saved.size());
    }

    @PatchMapping("/datasets/{id}/total-items")
    @Transactional
    public String updateTotalItems(@PathVariable UUID id, @RequestParam int value) {
        datasetRepository.findById(id).ifPresent(d -> {
            d.setTotalItems(value);
            datasetRepository.save(d);
        });
        return "ok";
    }

    public record BulkCreateRequest(String datasetId, String projectId, List<TaskItem> tasks) {}
    public record TaskItem(String payloadJson, String status) {}
    public record BulkCreateResponse(int created) {}
}
