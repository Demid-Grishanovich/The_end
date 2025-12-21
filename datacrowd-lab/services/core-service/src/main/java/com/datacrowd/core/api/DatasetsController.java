package com.datacrowd.core.api;

import com.datacrowd.core.dto.DatasetResponse;
import com.datacrowd.core.service.DatasetService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/core/projects/{projectId}/datasets")
public class DatasetsController {

    private final DatasetService datasetService;

    public DatasetsController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public DatasetResponse create(
            @PathVariable UUID projectId,
            @RequestPart("name") @NotBlank String name,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart("file") MultipartFile file
    ) {
        return datasetService.createWithUpload(projectId, name, description, file);
    }

    @GetMapping
    public List<DatasetResponse> list(@PathVariable UUID projectId) {
        return datasetService.list(projectId);
    }
}
