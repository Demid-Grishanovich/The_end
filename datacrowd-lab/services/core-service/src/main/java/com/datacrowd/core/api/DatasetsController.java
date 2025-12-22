package com.datacrowd.core.api;

import com.datacrowd.core.dto.DatasetResponse;
import com.datacrowd.core.dto.GenerateTasksRequest;
import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.DatasetService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/core")
public class DatasetsController {

    private final DatasetService datasetService;

    public DatasetsController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping(value = "/projects/{projectId}/datasets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DatasetResponse upload(
            @PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file
    ) {
        UUID userId = AuthContext.getUserIdOrThrow();
        DatasetEntity d = datasetService.upload(projectId, userId, file);
        return toResponse(d);
    }

    @PostMapping("/datasets/{datasetId}/generate-tasks")
    public String generate(
            @PathVariable UUID datasetId,
            @Valid @RequestBody GenerateTasksRequest req
    ) {
        UUID userId = AuthContext.getUserIdOrThrow();
        datasetService.generateTasks(datasetId, userId, req);
        return "ok";
    }

    private DatasetResponse toResponse(DatasetEntity d) {
        DatasetResponse r = new DatasetResponse();
        r.id = d.getId();
        r.projectId = d.getProjectId();
        r.sourcePath = d.getSourcePath();
        r.status = d.getStatus();
        r.totalItems = d.getTotalItems();
        r.createdAt = d.getCreatedAt();
        return r;
    }
}
