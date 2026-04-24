package com.datacrowd.core.service;

import com.datacrowd.core.dto.CreateProjectRequest;
import com.datacrowd.core.entity.AuditLogEntity;
import com.datacrowd.core.entity.BillingStatus;
import com.datacrowd.core.entity.DataType;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.entity.ProjectStatus;
import com.datacrowd.core.repo.ProjectRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AuditService      auditService;
    private final MetricsService metricsService;

    public ProjectService(ProjectRepository projectRepository,
                          AuditService auditService,
                          MetricsService metricsService) {
        this.projectRepository = projectRepository;
        this.auditService      = auditService;
        this.metricsService    = metricsService;
    }

    @Transactional
    public ProjectEntity create(UUID ownerUserId, CreateProjectRequest req) {
        ProjectEntity p = new ProjectEntity();
        p.setOwnerUserId(ownerUserId);
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setStatus(ProjectStatus.NEW);
        p.setDataType(req.getDataType() != null ? req.getDataType() : DataType.TEXT);
        p.setBillingStatus(BillingStatus.UNPAID);
        p.setTaskQuota(0);

        if (req.getReviewersCount()  != null) p.setReviewersCount(req.getReviewersCount());
        if (req.getRewardPoints()    != null) p.setRewardPoints(req.getRewardPoints());

        int minSec = (req.getMinAnswerSeconds() != null) ? req.getMinAnswerSeconds() : 3;
        p.setMinAnswerSeconds(minSec);

        ProjectEntity saved = projectRepository.save(p);

        auditService.log(ownerUserId, AuditLogEntity.PROJECT_CREATED,
                "PROJECT", saved.getId());
        metricsService.incrementProjectsCreated();

        return saved;
    }

    public List<ProjectEntity> myProjects(UUID ownerUserId) {
        return projectRepository.findAllByOwnerUserId(ownerUserId);
    }

    public Page<ProjectEntity> myProjects(UUID ownerUserId, Pageable pageable) {
        return projectRepository.findAllByOwnerUserId(ownerUserId, pageable);
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