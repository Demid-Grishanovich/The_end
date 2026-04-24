package com.datacrowd.core.service;

import com.datacrowd.core.api.ApiForbiddenException;
import com.datacrowd.core.api.ApiNotFoundException;
import com.datacrowd.core.entity.*;
import com.datacrowd.core.repo.*;
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
class ExportServiceTest {

    @Mock ExportRepository  exportRepository;
    @Mock ProjectRepository projectRepository;
    @Mock DatasetRepository datasetRepository;
    @Mock AnswerRepository  answerRepository;
    @Mock StorageService    storageService;

    @InjectMocks ExportService exportService;

    // -----------------------------------------------------------------------
    // Нельзя экспортировать чужой проект
    // -----------------------------------------------------------------------

    @Test
    void buildExport_throwsForbidden_whenNotOwner() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId   = UUID.randomUUID();
        UUID otherId   = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setOwnerUserId(ownerId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() ->
                exportService.buildExport(otherId, projectId, UUID.randomUUID(), "jsonl"))
                .isInstanceOf(ApiForbiddenException.class)
                .hasMessageContaining("Not your project");
    }

    // -----------------------------------------------------------------------
    // Проект не найден
    // -----------------------------------------------------------------------

    @Test
    void buildExport_throwsNotFound_whenProjectMissing() {
        UUID projectId = UUID.randomUUID();

        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                exportService.buildExport(UUID.randomUUID(), projectId, UUID.randomUUID(), "jsonl"))
                .isInstanceOf(ApiNotFoundException.class)
                .hasMessageContaining("Project not found");
    }

    // -----------------------------------------------------------------------
    // Датасет не принадлежит проекту
    // -----------------------------------------------------------------------

    @Test
    void buildExport_throwsConflict_whenDatasetNotInProject() {
        UUID projectId  = UUID.randomUUID();
        UUID ownerId    = UUID.randomUUID();
        UUID datasetId  = UUID.randomUUID();
        UUID otherProject = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setOwnerUserId(ownerId);

        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(datasetId);
        dataset.setProjectId(otherProject); // другой проект

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));

        assertThatThrownBy(() ->
                exportService.buildExport(ownerId, projectId, datasetId, "jsonl"))
                .isInstanceOf(com.datacrowd.core.api.ApiConflictException.class);
    }

    // -----------------------------------------------------------------------
    // Неподдерживаемый формат
    // -----------------------------------------------------------------------

    @Test
    void buildExport_throwsConflict_whenFormatUnsupported() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId   = UUID.randomUUID();
        UUID datasetId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setOwnerUserId(ownerId);
        project.setDataType(DataType.TEXT);

        DatasetEntity dataset = new DatasetEntity();
        dataset.setId(datasetId);
        dataset.setProjectId(projectId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));

        assertThatThrownBy(() ->
                exportService.buildExport(ownerId, projectId, datasetId, "xml"))
                .isInstanceOf(com.datacrowd.core.api.ApiConflictException.class)
                .hasMessageContaining("supported");
    }
}