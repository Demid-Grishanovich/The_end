package com.datacrowd.core.api;

import com.datacrowd.core.entity.ExportEntity;
import com.datacrowd.core.security.AuthContext;
import com.datacrowd.core.service.ExportService;
import com.datacrowd.core.service.StorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/core")
public class ExportsController {

    private final ExportService exportService;
    private final StorageService storageService;

    public ExportsController(ExportService exportService, StorageService storageService) {
        this.exportService = exportService;
        this.storageService = storageService;
    }

    // POST /core/projects/{projectId}/export?datasetId=...&format=jsonl
    @PostMapping("/projects/{projectId}/export")
    public Map<String, Object> export(
            @PathVariable UUID projectId,
            @RequestParam UUID datasetId,
            @RequestParam(required = false, defaultValue = "jsonl") String format
    ) {
        UUID userId = AuthContext.getUserIdOrThrow();
        ExportEntity ex = exportService.buildExport(userId, projectId, datasetId, format);

        return Map.of(
                "exportId", ex.getId(),
                "status", ex.getStatus(),
                "format", ex.getFormat(),
                "downloadUrl", "/core/exports/" + ex.getId() + "/download"
        );
    }

    // GET /core/exports/{exportId}/download
    @GetMapping("/exports/{exportId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID exportId) {
        UUID userId = AuthContext.getUserIdOrThrow();
        ExportEntity ex = exportService.getOwnedOrThrow(userId, exportId);

        byte[] data = storageService.readBytes(ex.getFilePath());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"result." + ex.getFormat() + "\"")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(data);
    }
}
