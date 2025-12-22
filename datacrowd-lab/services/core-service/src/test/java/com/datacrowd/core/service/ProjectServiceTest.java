package com.datacrowd.core.service;

import com.datacrowd.core.dto.CreateProjectRequest;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.entity.ProjectStatus;
import com.datacrowd.core.repo.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock ProjectRepository projectRepository;
    @InjectMocks ProjectService projectService;

    @Captor ArgumentCaptor<ProjectEntity> projectCaptor;

    @Test
    void create_setsDefaultsAndSaves() {
        UUID ownerId = UUID.randomUUID();
        CreateProjectRequest req = new CreateProjectRequest();
        req.setName("My project");
        req.setDescription("Desc");
        req.setDataType("IMAGE");
        req.setReviewersCount(2);
        req.setRewardPoints(10);

        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectEntity saved = projectService.create(ownerId, req);

        verify(projectRepository).save(projectCaptor.capture());
        ProjectEntity p = projectCaptor.getValue();

        assertThat(p.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(p.getName()).isEqualTo("My project");
        assertThat(p.getDescription()).isEqualTo("Desc");
        assertThat(p.getDataType()).isEqualTo("IMAGE");
        assertThat(p.getStatus()).isEqualTo(ProjectStatus.NEW);
        assertThat(p.getReviewersCount()).isEqualTo(2);
        assertThat(p.getRewardPoints()).isEqualTo(10);

        // MVP billing/quota defaults
        assertThat(p.getBillingStatus()).isEqualTo("UNPAID");
        assertThat(p.getTaskQuota()).isEqualTo(0);

        assertThat(saved).isSameAs(p);
    }

    @Test
    void myProjects_delegatesToRepository() {
        UUID ownerId = UUID.randomUUID();
        when(projectRepository.findAllByOwnerUserId(ownerId)).thenReturn(List.of(new ProjectEntity()));

        List<ProjectEntity> res = projectService.myProjects(ownerId);

        assertThat(res).hasSize(1);
        verify(projectRepository).findAllByOwnerUserId(ownerId);
    }

    @Test
    void getOwnedOrThrow_throwsWhenNotFound() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getOwnedOrThrow(projectId, ownerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    void getOwnedOrThrow_throwsWhenOwnerMismatch() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        ProjectEntity p = new ProjectEntity();
        p.setId(projectId);
        p.setOwnerUserId(UUID.randomUUID());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> projectService.getOwnedOrThrow(projectId, ownerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Forbidden");
    }

    @Test
    void getOwnedOrThrow_returnsProjectWhenOwnerMatches() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        ProjectEntity p = new ProjectEntity();
        p.setId(projectId);
        p.setOwnerUserId(ownerId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(p));

        ProjectEntity res = projectService.getOwnedOrThrow(projectId, ownerId);

        assertThat(res).isSameAs(p);
    }
}
