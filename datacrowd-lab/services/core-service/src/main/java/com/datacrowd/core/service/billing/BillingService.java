package com.datacrowd.core.service.billing;

import com.datacrowd.core.entity.BillingStatus;
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
    public void grantPaidAccess(UUID projectId, int taskQuotaDelta, String billingStatusStr) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project not found: " + projectId));

        // ИЗМЕНЕНО: парсим строку в enum безопасно
        if (billingStatusStr != null && !billingStatusStr.isBlank()) {
            try {
                project.setBillingStatus(BillingStatus.valueOf(billingStatusStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Если пришло неизвестное значение — ставим PAID по умолчанию
                project.setBillingStatus(BillingStatus.PAID);
            }
        } else {
            project.setBillingStatus(BillingStatus.PAID);
        }

        int current = (project.getTaskQuota() != null) ? project.getTaskQuota() : 0;
        project.setTaskQuota(current + Math.max(taskQuotaDelta, 0));

        projectRepository.save(project);
    }
}