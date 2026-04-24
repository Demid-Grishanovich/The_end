package com.datacrowd.core.service;

import com.datacrowd.core.entity.BillingStatus;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.service.billing.BillingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock ProjectRepository projectRepository;

    @InjectMocks BillingService billingService;

    @Test
    void grantPaidAccess_setsBillingStatusPaid_andAddsQuota() {
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setBillingStatus(BillingStatus.UNPAID);
        project.setTaskQuota(0);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        billingService.grantPaidAccess(projectId, 100, "PAID");

        assertThat(project.getBillingStatus()).isEqualTo(BillingStatus.PAID);
        assertThat(project.getTaskQuota()).isEqualTo(100);
        verify(projectRepository).save(project);
    }

    @Test
    void grantPaidAccess_addsToExistingQuota() {
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setBillingStatus(BillingStatus.UNPAID);
        project.setTaskQuota(500); // уже было 500

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        billingService.grantPaidAccess(projectId, 200, "PAID");

        assertThat(project.getTaskQuota()).isEqualTo(700); // 500 + 200
    }

    @Test
    void grantPaidAccess_withNullBillingStatus_defaultsToPaid() {
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setTaskQuota(0);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        billingService.grantPaidAccess(projectId, 50, null);

        assertThat(project.getBillingStatus()).isEqualTo(BillingStatus.PAID);
    }

    @Test
    void grantPaidAccess_withUnknownStatus_defaultsToPaid() {
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setTaskQuota(0);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        billingService.grantPaidAccess(projectId, 50, "UNKNOWN_STATUS");

        assertThat(project.getBillingStatus()).isEqualTo(BillingStatus.PAID);
    }

    @Test
    void grantPaidAccess_throwsNotFound_whenProjectMissing() {
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                billingService.grantPaidAccess(projectId, 100, "PAID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void grantPaidAccess_ignoresNegativeDelta() {
        UUID projectId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setTaskQuota(100);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        billingService.grantPaidAccess(projectId, -50, "PAID");

        // Отрицательная дельта не должна уменьшать квоту — Math.max(delta, 0)
        assertThat(project.getTaskQuota()).isEqualTo(100);
    }
}