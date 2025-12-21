package com.datacrowd.core.service;

import com.datacrowd.core.dto.CreateProjectRequest;
import com.datacrowd.core.dto.ProjectResponse;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.entity.ProjectStatus;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.security.AuthContext;
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
    public ProjectResponse create(CreateProjectRequest req) {
        UUID userId = AuthContext.requireUserId();

        ProjectEntity p = new ProjectEntity();
        p.setOwnerUserId(userId);
        p.setTitle(req.getTitle());
        p.setDescription(req.getDescription());
        p.setRewardPoints(req.getRewardPoints());
        p.setReviewersCount(req.getReviewersCount());
        p.setDataType(req.getDataType());
        p.setTaskQuota(req.getTaskQuota());
        p.setStatus(ProjectStatus.DRAFT);

        ProjectEntity saved = projectRepository.save(p);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listMine() {
        UUID userId = AuthContext.requireUserId();
        return projectRepository.findAllByOwnerUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectEntity requireOwnedProject(UUID projectId) {
        UUID userId = AuthContext.requireUserId();
        ProjectEntity p = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (p.getOwnerUserId() == null || !p.getOwnerUserId().equals(userId)) {
            throw new IllegalStateException("Access denied: not owner");
        }
        return p;
    }

    public ProjectResponse toResponse(ProjectEntity p) {
        ProjectResponse r = new ProjectResponse();
        r.id = p.getId();
        r.ownerUserId = p.getOwnerUserId();
        r.title = p.getTitle();
        r.description = p.getDescription();
        r.rewardPoints = p.getRewardPoints();
        r.status = p.getStatus();
        r.reviewersCount = p.getReviewersCount();
        r.dataType = p.getDataType();
        r.billingStatus = p.getBillingStatus();
        r.taskQuota = p.getTaskQuota();
        r.createdAt = p.getCreatedAt();
        r.updatedAt = p.getUpdatedAt();
        return r;
    }
}
