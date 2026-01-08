package com.datacrowd.core.service.billing;

import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.repo.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class BillingService {

    private final ProjectRepository projectRepository;

    public BillingService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public void grantPaidAccess(UUID projectId, int taskQuotaDelta, String billingStatus) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // выставляем PAID
        if (billingStatus != null && !billingStatus.isBlank()) {
            project.setBillingStatus(billingStatus);
        } else {
            project.setBillingStatus("PAID");
        }

        // увеличиваем quota
        int current = (project.getTaskQuota() != null) ? project.getTaskQuota() : 0;
        project.setTaskQuota(current + Math.max(taskQuotaDelta, 0));

        projectRepository.save(project);
    }
}
