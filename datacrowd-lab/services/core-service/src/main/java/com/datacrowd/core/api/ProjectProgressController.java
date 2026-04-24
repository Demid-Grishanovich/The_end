package com.datacrowd.core.api;

import com.datacrowd.core.repo.TaskRepository;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Tag(name = "Projects", description = "Client project management")
@RestController
@RequestMapping("/core/projects")
public class ProjectProgressController {

    private final TaskRepository taskRepository;
    private final ProjectService projectService;

    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(10);

    public ProjectProgressController(TaskRepository taskRepository,
                                     ProjectService projectService) {
        this.taskRepository = taskRepository;
        this.projectService = projectService;
    }


    @Operation(
            summary     = "SSE stream of project progress",
            description = "Server-Sent Events stream. Updates every 2 seconds. " +
                    "Closes automatically when progress = 100%. " +
                    "Frontend: const es = new EventSource('/api/core/projects/{id}/progress/stream'); " +
                    "es.addEventListener('progress', e => { const d = JSON.parse(e.data); ... });"
    )
    @GetMapping(
            value    = "/{id}/progress/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter progressStream(@PathVariable UUID id) {
        UUID userId = AuthContext.getUserIdOrThrow();

        projectService.getOwnedOrThrow(id, userId);

        SseEmitter emitter = new SseEmitter(300_000L);

        ScheduledFuture<?>[] futureHolder = new ScheduledFuture<?>[1];

        Runnable task = () -> {
            try {
                long total     = taskRepository.countAllByProject(id);
                long completed = taskRepository.countApprovedByProject(id);
                int  progress  = total > 0 ? (int) (completed * 100 / total) : 0;
                String status  = progress >= 100 ? "COMPLETED" : "IN_PROGRESS";

                Map<String, Object> data = Map.of(
                        "projectId", id.toString(),
                        "completed", completed,
                        "total",     total,
                        "progress",  progress,
                        "status",    status
                );

                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(data, MediaType.APPLICATION_JSON));


                if (progress >= 100) {
                    emitter.complete();
                    if (futureHolder[0] != null) {
                        futureHolder[0].cancel(false);
                    }
                }

            } catch (IOException e) {
                emitter.completeWithError(e);
                if (futureHolder[0] != null) {
                    futureHolder[0].cancel(false);
                }
            } catch (Exception e) {
            }
        };

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                task, 0, 2, TimeUnit.SECONDS
        );
        futureHolder[0] = future;

        emitter.onCompletion(() -> future.cancel(false));
        emitter.onTimeout(() -> {
            future.cancel(false);
            emitter.complete();
        });
        emitter.onError(e -> future.cancel(false));

        return emitter;
    }
}