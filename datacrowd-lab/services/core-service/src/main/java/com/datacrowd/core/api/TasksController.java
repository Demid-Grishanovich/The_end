package com.datacrowd.core.api;

import com.datacrowd.core.dto.SubmitTaskRequest;
import com.datacrowd.core.dto.SubmitTaskResponse;
import com.datacrowd.core.dto.TaskResponse;
import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.WorkerTaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/core/tasks")
public class TasksController {

    private final WorkerTaskService workerTaskService;

    public TasksController(WorkerTaskService workerTaskService) {
        this.workerTaskService = workerTaskService;
    }

    @GetMapping("/next")
    public ResponseEntity<TaskResponse> next() {
        UUID userId = AuthContext.getUserIdOrThrow();
        return workerTaskService.nextTask(userId)
                .map(t -> ResponseEntity.ok(toResponse(t)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/lock")
    public TaskResponse lock(@PathVariable UUID id) {
        UUID userId = AuthContext.getUserIdOrThrow();
        TaskEntity t = workerTaskService.lock(id, userId);
        return toResponse(t);
    }

    @PostMapping("/{id}/unlock")
    public TaskResponse unlock(@PathVariable UUID id) {
        UUID userId = AuthContext.getUserIdOrThrow();
        TaskEntity t = workerTaskService.unlock(id, userId);
        return toResponse(t);
    }

    @PostMapping("/{id}/submit")
    public SubmitTaskResponse submit(@PathVariable UUID id, @Valid @RequestBody SubmitTaskRequest req) {
        UUID userId = AuthContext.getUserIdOrThrow();
        WorkerTaskService.SubmitResult res = workerTaskService.submit(id, userId, req.answerJson);

        SubmitTaskResponse out = new SubmitTaskResponse();
        out.taskId = res.task().getId();
        out.answerId = res.answer().getId();
        out.taskStatus = res.task().getStatus().name();
        out.pointsAwarded = res.pointsAwarded();
        return out;
    }

    private TaskResponse toResponse(TaskEntity t) {
        TaskResponse r = new TaskResponse();
        r.id = t.getId();
        r.projectId = t.getProjectId();
        r.datasetId = t.getDatasetId();
        r.batchId = t.getBatchId();
        r.payloadJson = t.getPayloadJson();
        r.status = t.getStatus() != null ? t.getStatus().name() : null;
        r.lockedByUserId = t.getLockedByUserId();
        r.lockedAt = t.getLockedAt();
        r.createdAt = t.getCreatedAt();
        return r;
    }
}
