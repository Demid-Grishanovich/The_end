package com.datacrowd.core.api;

import com.datacrowd.core.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
public class InternalTasksController {

    private final TaskService taskService;

    public InternalTasksController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/projects/{projectId}/datasets/{datasetId}/generate-tasks")
    public Map<String, Object> generate(
            @PathVariable UUID projectId,
            @PathVariable UUID datasetId,
            @RequestParam(defaultValue = "10") int count
    ) {
        int created = taskService.generateTasks(projectId, datasetId, count);
        return Map.of(
                "status", "ok",
                "created", created,
                "projectId", projectId,
                "datasetId", datasetId
        );
    }
}
