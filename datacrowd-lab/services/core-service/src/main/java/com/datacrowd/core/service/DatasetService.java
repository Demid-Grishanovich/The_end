package com.datacrowd.core.service;

import com.datacrowd.core.dto.GenerateTasksRequest;
import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.entity.DatasetStatus;
import com.datacrowd.core.entity.DatasetSourceType;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.datacrowd.core.entity.BillingStatus;

import java.util.HashMap;
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

        // Optional: if uploaded as .zip, extract and locate manifest.jsonl for multimedia datasets.
        String originalName = (file != null ? file.getOriginalFilename() : null);
        if (originalName != null && originalName.toLowerCase().endsWith(".zip")) {
            String manifestPath = storageService.extractDatasetZipAndFindManifest(d.getId(), sourcePath);
            d.setSourceType(DatasetSourceType.ZIP_MANIFEST);
            d.setManifestPath(manifestPath);
        } else {
            d.setSourceType(DatasetSourceType.FILE);
            d.setManifestPath(null);
        }

        return datasetRepository.save(d);
    }

    @Transactional
    public void generateTasks(UUID datasetId, UUID ownerUserId, GenerateTasksRequest req) {
        DatasetEntity d = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        ProjectEntity p = projectRepository.findById(d.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!p.getOwnerUserId().equals(ownerUserId)) {
            throw new IllegalStateException("Forbidden: not project owner");
        }

        // Check billing — must be paid OR have quota
        boolean paid     = p.getBillingStatus() == BillingStatus.PAID;
        boolean hasQuota = p.getTaskQuota() != null && p.getTaskQuota() > 0;
        if (!paid && !hasQuota) {
            throw new IllegalStateException("Project is not paid. Please complete payment first.");
        }

        if (d.getSourcePath() == null || d.getSourcePath().isBlank()) {
            throw new IllegalStateException("Dataset has no source file uploaded.");
        }

        d.setStatus(DatasetStatus.GENERATING);
        datasetRepository.save(d);

        // Build runner request body
        Map<String, Object> body = new HashMap<>();
        body.put("datasetId",      datasetId.toString());
        body.put("projectId",      p.getId().toString());
        body.put("sourcePath",     d.getSourcePath());
        body.put("sourceType",     d.getSourceType() != null ? d.getSourceType().name() : null);
        body.put("manifestPath",   d.getManifestPath());
        body.put("reviewersCount", p.getReviewersCount() != null ? p.getReviewersCount() : 1);
        body.put("rewardPoints",   p.getRewardPoints()   != null ? p.getRewardPoints()   : 0);
        // batchSize removed — runner no longer uses batches

        runnerClient.triggerGenerate(datasetId, body);
    }

    @Transactional
    public void updateDatasetStatusInternal(UUID datasetId, DatasetStatus status) {
        DatasetEntity d = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));
        d.setStatus(status);
        datasetRepository.save(d);
    }

    @Transactional
    public void updateDatasetTotalItemsInternal(UUID datasetId, int totalItems) {
        DatasetEntity d = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found"));
        if (totalItems < 0) totalItems = 0;
        d.setTotalItems(totalItems);
        datasetRepository.save(d);
    }
}
