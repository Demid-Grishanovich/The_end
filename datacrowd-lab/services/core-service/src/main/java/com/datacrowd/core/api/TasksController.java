package com.datacrowd.core.api;

import com.datacrowd.core.dto.SubmitTaskRequest;
import com.datacrowd.core.dto.SubmitTaskResponse;
import com.datacrowd.core.dto.TaskResponse;
import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.repo.TaskRepository;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.WorkerTaskService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/core/tasks")
public class TasksController {

    private final WorkerTaskService workerTaskService;
    private final TaskRepository    taskRepository;

    public TasksController(WorkerTaskService workerTaskService,
                           TaskRepository taskRepository) {
        this.workerTaskService = workerTaskService;
        this.taskRepository    = taskRepository;
    }

    // НОВОЕ: получить задачу по ID
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getById(@PathVariable UUID id) {
        return taskRepository.findById(id)
                .map(t -> ResponseEntity.ok(toResponse(t)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/next")
    public ResponseEntity<TaskResponse> next(
            @RequestParam(required = false) UUID projectId) {
        UUID userId = AuthContext.getUserIdOrThrow();
        Optional<TaskEntity> task = projectId != null
                ? workerTaskService.nextTaskForProject(userId, projectId)
                : workerTaskService.nextTask(userId);
        return task
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
    public SubmitTaskResponse submit(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitTaskRequest req) {

        UUID userId = AuthContext.getUserIdOrThrow();
        WorkerTaskService.SubmitResult res = workerTaskService.submit(id, userId, req.answerJson);

        SubmitTaskResponse out = new SubmitTaskResponse();
        out.taskId       = res.task().getId();
        out.answerId     = res.answer().getId();
        out.taskStatus   = res.task().getStatus().name();
        out.pointsAwarded = res.pointsAwarded();
        return out;
    }

    @GetMapping("/{id}/asset")
    public ResponseEntity<?> getAsset(
            @PathVariable UUID id,
            @RequestHeader HttpHeaders headers) {

        UUID userId = AuthContext.getUserIdOrThrow();
        Path file   = workerTaskService.resolveTaskAssetPath(id, userId);

        String contentType;
        try {
            contentType = Files.probeContentType(file);
        } catch (Exception e) {
            contentType = null;
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        Resource resource = new FileSystemResource(file);

        // Поддержка Range запросов для аудио/видео
        var ranges = headers.getRange();
        if (ranges != null && !ranges.isEmpty()) {
            long contentLength;
            try {
                contentLength = resource.contentLength();
            } catch (Exception e) {
                contentLength = -1;
            }

            HttpRange range        = ranges.get(0);
            long      start        = range.getRangeStart(contentLength);
            long      end          = range.getRangeEnd(contentLength);
            long      regionLength = Math.max(0, end - start + 1);

            ResourceRegion region = new ResourceRegion(resource, start, regionLength);
            return ResponseEntity.status(206)
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(region);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    private TaskResponse toResponse(TaskEntity t) {
        TaskResponse r = new TaskResponse();
        r.id             = t.getId();
        r.projectId      = t.getProjectId();
        r.datasetId      = t.getDatasetId();
        r.batchId        = t.getBatchId();
        r.payloadJson    = t.getPayloadJson();
        r.status         = t.getStatus() != null ? t.getStatus().name() : null;
        r.lockedByUserId = t.getLockedByUserId();
        r.lockedAt       = t.getLockedAt();
        r.createdAt      = t.getCreatedAt();
        return r;
    }
}