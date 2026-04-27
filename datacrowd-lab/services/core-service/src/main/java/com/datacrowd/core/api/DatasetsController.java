package com.datacrowd.core.api;

import com.datacrowd.core.dto.DatasetResponse;
import com.datacrowd.core.dto.GenerateTasksRequest;
import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.FailedItemRepository;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.DatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.datacrowd.core.service.ProjectService;
import java.util.List;
import java.util.stream.Collectors;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Datasets", description = "Dataset upload and task generation")
@RestController
@RequestMapping("/core")
public class DatasetsController {

    private final DatasetService       datasetService;
    private final DatasetRepository    datasetRepository;
    private final FailedItemRepository failedItemRepository;
    private final ProjectService projectService;

    public DatasetsController(DatasetService datasetService,
                              DatasetRepository datasetRepository,
                              FailedItemRepository failedItemRepository,ProjectService projectService) {
        this.datasetService       = datasetService;
        this.datasetRepository    = datasetRepository;
        this.failedItemRepository = failedItemRepository;
        this.projectService       = projectService;
    }

    @Operation(summary = "Get dataset by ID")
    @GetMapping("/datasets/{datasetId}")
    public ResponseEntity<DatasetResponse> getById(@PathVariable UUID datasetId) {
        return datasetRepository.findById(datasetId)
                .map(d -> ResponseEntity.ok(toResponse(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(
            summary     = "Get dataset status",
            description = "Lightweight endpoint for polling. " +
                    "Frontend calls every 3 seconds until status = READY or FAILED."
    )
    @GetMapping("/datasets/{datasetId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable UUID datasetId) {
        return datasetRepository.findById(datasetId)
                .map(d -> ResponseEntity.ok(Map.<String, Object>of(
                        "datasetId",  d.getId().toString(),
                        "status",     d.getStatus().name(),
                        "totalItems", d.getTotalItems()
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get datasets for a project")
    @GetMapping("/projects/{projectId}/datasets")
    public List<DatasetResponse> getByProject(@PathVariable UUID projectId) {
        UUID userId = AuthContext.getUserIdOrThrow();
        // verify ownership
        projectService.getOwnedOrThrow(projectId, userId);
        return datasetRepository.findAllByProjectId(projectId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Operation(
            summary     = "Get dataset summary with DLQ info",
            description = "Returns dataset status, total tasks created and failed rows count (DLQ). " +
                    "Example: '990 tasks created, 10 rows had errors.'"
    )
    @GetMapping("/datasets/{datasetId}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable UUID datasetId) {
        return datasetRepository.findById(datasetId)
                .map(d -> {
                    long failedCount = failedItemRepository.countByDatasetId(datasetId);
                    String message;
                    switch (d.getStatus()) {
                        case UPLOADED   -> message = "Dataset uploaded, waiting for task generation.";
                        case GENERATING -> message = "Generating tasks...";
                        case READY      -> message = failedCount > 0
                                ? d.getTotalItems() + " tasks created, " + failedCount + " rows had errors."
                                : d.getTotalItems() + " tasks created successfully.";
                        case FAILED     -> message = "Processing failed. " + failedCount + " rows with errors.";
                        default         -> message = d.getStatus().name();
                    }
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "datasetId",   d.getId().toString(),
                            "status",      d.getStatus().name(),
                            "totalItems",  d.getTotalItems(),
                            "failedItems", failedCount,
                            "message",     message
                    ));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Upload dataset file")
    @PostMapping(
            value    = "/projects/{projectId}/datasets",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public DatasetResponse upload(
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file) {
        UUID userId = AuthContext.getUserIdOrThrow();
        DatasetEntity d = datasetService.upload(projectId, userId, file);
        return toResponse(d);
    }

    @Operation(summary = "Generate tasks from dataset")
    @PostMapping("/datasets/{datasetId}/generate-tasks")
    public String generate(
            @PathVariable UUID datasetId,
            @Valid @RequestBody GenerateTasksRequest req) {
        UUID userId = AuthContext.getUserIdOrThrow();
        datasetService.generateTasks(datasetId, userId, req);
        return "ok";
    }

    private DatasetResponse toResponse(DatasetEntity d) {
        DatasetResponse r = new DatasetResponse();
        r.id           = d.getId();
        r.projectId    = d.getProjectId();
        r.sourcePath   = d.getSourcePath();
        r.sourceType   = d.getSourceType() != null ? d.getSourceType().name() : null;
        r.manifestPath = d.getManifestPath();
        r.status       = d.getStatus();
        r.totalItems   = d.getTotalItems();
        r.createdAt    = d.getCreatedAt();
        return r;
    }
}