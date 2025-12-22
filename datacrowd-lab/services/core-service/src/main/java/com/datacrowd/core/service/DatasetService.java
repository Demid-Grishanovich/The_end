package com.datacrowd.core.service;

import com.datacrowd.core.dto.GenerateTasksRequest;
import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.entity.DatasetStatus;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final ProjectRepository projectRepository;
    private final StorageService storageService;
    private final RunnerClient runnerClient;

    public DatasetService(
            DatasetRepository datasetRepository,
            ProjectRepository projectRepository,
            StorageService storageService,
            RunnerClient runnerClient
    ) {
        this.datasetRepository = datasetRepository;
        this.projectRepository = projectRepository;
        this.storageService = storageService;
        this.runnerClient = runnerClient;
    }

    @Transactional
    public DatasetEntity upload(UUID projectId, UUID ownerUserId, MultipartFile file) {
        ProjectEntity p = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!p.getOwnerUserId().equals(ownerUserId)) {
            throw new IllegalStateException("Forbidden: not project owner");
        }

        DatasetEntity d = new DatasetEntity();
        d.setProjectId(projectId);
        d.setStatus(DatasetStatus.UPLOADED);

        // IMPORTANT: in DB migration V1 datasets.name is NOT NULL.
        // We fill it from filename to avoid insert errors.
        String fileName = (file != null ? file.getOriginalFilename() : null);
        if (fileName == null || fileName.isBlank()) {
            fileName = "dataset";
        }
        d.setName(fileName);
        d.setTotalItems(0);

        d = datasetRepository.save(d);

        String sourcePath = storageService.saveDatasetSource(d.getId(), file);
        d.setSourcePath(sourcePath);

        return datasetRepository.save(d);
    }

    @Transactional
    public void generateTasks(UUID datasetId, UUID ownerUserId, GenerateTasksRequest req) {
        DatasetEntity d = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));

        ProjectEntity p = projectRepository.findById(d.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!p.getOwnerUserId().equals(ownerUserId)) {
            throw new IllegalStateException("Forbidden: not project owner");
        }

        // Проверка оплаты/квоты (MVP согласно плану)
        boolean paid = "PAID".equalsIgnoreCase(String.valueOf(p.getBillingStatus()));
        boolean hasQuota = p.getTaskQuota() != null && p.getTaskQuota() > 0;
        if (!paid && !hasQuota) {
            throw new IllegalStateException("Project is not paid and has no task quota");
        }

        if (d.getSourcePath() == null || d.getSourcePath().isBlank()) {
            throw new IllegalStateException("Dataset sourcePath is empty");
        }

        d.setStatus(DatasetStatus.GENERATING);
        datasetRepository.save(d);

        Map<String, Object> body = Map.of(
                "datasetId", datasetId.toString(),
                "sourcePath", d.getSourcePath(),
                "batchSize", req.batchSize,
                "reviewersCount", req.reviewersCount,
                "rewardPoints", req.rewardPoints
        );

        runnerClient.triggerGenerate(datasetId, body);
    }

    @Transactional
    public void updateDatasetStatusInternal(UUID datasetId, DatasetStatus status) {
        DatasetEntity d = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));
        d.setStatus(status);
        datasetRepository.save(d);
    }
}
