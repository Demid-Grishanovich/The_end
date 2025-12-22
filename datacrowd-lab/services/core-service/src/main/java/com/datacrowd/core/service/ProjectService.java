package com.datacrowd.core.service;

import com.datacrowd.core.dto.CreateProjectRequest;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.entity.ProjectStatus;
import com.datacrowd.core.repo.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectEntity create(UUID ownerUserId, CreateProjectRequest req) {
        ProjectEntity p = new ProjectEntity();
        p.setOwnerUserId(ownerUserId);
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setDataType(req.getDataType());
        p.setStatus(ProjectStatus.NEW);

        if (req.getReviewersCount() != null) p.setReviewersCount(req.getReviewersCount());
        if (req.getRewardPoints() != null) p.setRewardPoints(req.getRewardPoints());

        // ✅ MVP billing/quota: чтобы не падало на NOT NULL в БД
        // По умолчанию проект UNPAID и квота 0 (генерить нельзя — это проверишь в DatasetService)
        p.setBillingStatus("UNPAID");
        p.setTaskQuota(0);

        return projectRepository.save(p);
    }

    public List<ProjectEntity> myProjects(UUID ownerUserId) {
        return projectRepository.findAllByOwnerUserId(ownerUserId);
    }

    public ProjectEntity getOwnedOrThrow(UUID projectId, UUID ownerUserId) {
        ProjectEntity p = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!p.getOwnerUserId().equals(ownerUserId)) {
            throw new IllegalStateException("Forbidden: not project owner");
        }
        return p;
    }
}
