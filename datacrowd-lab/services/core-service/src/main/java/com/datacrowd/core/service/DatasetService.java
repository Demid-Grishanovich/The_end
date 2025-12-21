package com.datacrowd.core.service;

import com.datacrowd.core.dto.DatasetResponse;
import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.entity.DatasetStatus;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.repo.DatasetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final ProjectService projectService;

    private final Path dataDir;

    public DatasetService(
            DatasetRepository datasetRepository,
            ProjectService projectService,
            @Value("${app.data.dir:/data}") String dataDir
    ) {
        this.datasetRepository = datasetRepository;
        this.projectService = projectService;
        this.dataDir = Paths.get(dataDir);
    }

    @Transactional
    public DatasetResponse createWithUpload(UUID projectId, String name, String description, MultipartFile file) {
        ProjectEntity project = projectService.requireOwnedProject(projectId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }

        DatasetEntity d = new DatasetEntity();
        d.setProject(project);
        d.setName(name);
        d.setDescription(description);
        d.setStatus(DatasetStatus.NEW);

        DatasetEntity saved = datasetRepository.save(d);

        // сохраняем файл в /data/datasets/<datasetId>/source.<ext>
        String ext = guessExt(file.getOriginalFilename());
        Path datasetFolder = dataDir.resolve("datasets").resolve(saved.getId().toString());
        Path target = datasetFolder.resolve("source" + ext);

        try {
            Files.createDirectories(datasetFolder);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save dataset file: " + e.getMessage(), e);
        }

        saved.setSourcePath(target.toString());
        saved.setStatus(DatasetStatus.UPLOADED);

        DatasetEntity updated = datasetRepository.save(saved);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<DatasetResponse> list(UUID projectId) {
        // ownership check by loading project
        projectService.requireOwnedProject(projectId);
        return datasetRepository.findAllByProjectId(projectId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DatasetEntity requireOwnedDataset(UUID projectId, UUID datasetId) {
        ProjectEntity project = projectService.requireOwnedProject(projectId);

        DatasetEntity d = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        if (!d.getProject().getId().equals(project.getId())) {
            throw new IllegalStateException("Dataset does not belong to project");
        }
        return d;
    }

    public DatasetResponse toResponse(DatasetEntity d) {
        DatasetResponse r = new DatasetResponse();
        r.id = d.getId();
        r.projectId = d.getProject().getId();
        r.name = d.getName();
        r.description = d.getDescription();
        r.sourcePath = d.getSourcePath();
        r.status = d.getStatus();
        r.totalItems = d.getTotalItems();
        r.createdAt = d.getCreatedAt();
        return r;
    }

    private static String guessExt(String originalName) {
        if (originalName == null) return "";
        int idx = originalName.lastIndexOf('.');
        if (idx < 0) return "";
        String ext = originalName.substring(idx).trim().toLowerCase();
        if (ext.length() > 10) return "";
        return ext;
    }
}
