package com.datacrowd.core.service;

import com.datacrowd.core.dto.GenerateTasksRequest;
import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.entity.DatasetStatus;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    @Mock DatasetRepository datasetRepository;
    @Mock ProjectRepository projectRepository;
    @Mock StorageService storageService;
    @Mock RunnerClient runnerClient;

    @InjectMocks DatasetService datasetService;

    @Captor ArgumentCaptor<Map<String, Object>> mapCaptor;

    UUID ownerId;
    UUID projectId;
    UUID datasetId;
    ProjectEntity project;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        datasetId = UUID.randomUUID();

        project = new ProjectEntity();
        project.setId(projectId);
        project.setOwnerUserId(ownerId);
        project.setBillingStatus("PAID");
        project.setTaskQuota(0);
    }

    @Test
    void upload_setsNameFromFilename_setsStatusAndSavesTwice() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("my.csv");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        when(datasetRepository.save(any(DatasetEntity.class)))
                .thenAnswer(inv -> {
                    DatasetEntity d = inv.getArgument(0);
                    if (d.getId() == null) d.setId(datasetId);
                    return d;
                });

        when(storageService.saveDatasetSource(eq(datasetId), eq(file))).thenReturn("s3://bucket/path");

        DatasetEntity result = datasetService.upload(projectId, ownerId, file);

        verify(datasetRepository, times(2)).save(any(DatasetEntity.class));
        verify(storageService).saveDatasetSource(datasetId, file);

        assertThat(result.getId()).isEqualTo(datasetId);
        assertThat(result.getSourcePath()).isEqualTo("s3://bucket/path");
        assertThat(result.getStatus()).isEqualTo(DatasetStatus.UPLOADED);
        assertThat(result.getName()).isEqualTo("my.csv");
    }

    @Test
    void upload_throwsWhenNotOwner() {
        ProjectEntity otherOwner = new ProjectEntity();
        otherOwner.setId(projectId);
        otherOwner.setOwnerUserId(UUID.randomUUID());

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(otherOwner));

        assertThatThrownBy(() -> datasetService.upload(projectId, ownerId, mock(MultipartFile.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Forbidden");

        verifyNoInteractions(datasetRepository, storageService);
    }

    @Test
    void upload_usesDefaultNameWhenFilenameMissing() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("   ");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        when(datasetRepository.save(any(DatasetEntity.class)))
                .thenAnswer(inv -> {
                    DatasetEntity d = inv.getArgument(0);
                    if (d.getId() == null) d.setId(datasetId);
                    return d;
                });

        when(storageService.saveDatasetSource(eq(datasetId), eq(file))).thenReturn("path");

        DatasetEntity result = datasetService.upload(projectId, ownerId, file);

        assertThat(result.getName()).isEqualTo("dataset");
    }

    @Test
    void generateTasks_throwsWhenProjectNotPaidAndNoQuota() {
        project.setBillingStatus("UNPAID");
        project.setTaskQuota(0);

        DatasetEntity d = new DatasetEntity();
        d.setId(datasetId);
        d.setProjectId(projectId);
        d.setSourcePath("path");

        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(d));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> datasetService.generateTasks(datasetId, ownerId, new GenerateTasksRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not paid");

        verifyNoInteractions(runnerClient);
    }

    @Test
    void generateTasks_throwsWhenSourcePathEmpty() {
        DatasetEntity d = new DatasetEntity();
        d.setId(datasetId);
        d.setProjectId(projectId);
        d.setSourcePath("   ");

        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(d));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> datasetService.generateTasks(datasetId, ownerId, new GenerateTasksRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sourcePath");

        verifyNoInteractions(runnerClient);
    }

    @Test
    void generateTasks_setsGenerating_savesAndCallsRunner() {
        DatasetEntity d = new DatasetEntity();
        d.setId(datasetId);
        d.setProjectId(projectId);
        d.setSourcePath("s3://bucket/file.csv");
        d.setStatus(DatasetStatus.UPLOADED);

        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(d));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(datasetRepository.save(any(DatasetEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GenerateTasksRequest req = new GenerateTasksRequest();
        req.batchSize = 50;
        req.reviewersCount = 2;
        req.rewardPoints = 5;

        datasetService.generateTasks(datasetId, ownerId, req);

        assertThat(d.getStatus()).isEqualTo(DatasetStatus.GENERATING);

        verify(runnerClient).triggerGenerate(eq(datasetId), mapCaptor.capture());
        Map<String, Object> body = mapCaptor.getValue();

        assertThat(body.get("datasetId")).isEqualTo(datasetId.toString());
        assertThat(body.get("sourcePath")).isEqualTo("s3://bucket/file.csv");
        assertThat(body.get("batchSize")).isEqualTo(50);
        assertThat(body.get("reviewersCount")).isEqualTo(2);
        assertThat(body.get("rewardPoints")).isEqualTo(5);
    }

    @Test
    void updateDatasetStatusInternal_updatesAndSaves() {
        DatasetEntity d = new DatasetEntity();
        d.setId(datasetId);
        d.setStatus(DatasetStatus.UPLOADED);

        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(d));

        datasetService.updateDatasetStatusInternal(datasetId, DatasetStatus.READY);

        assertThat(d.getStatus()).isEqualTo(DatasetStatus.READY);
        verify(datasetRepository).save(d);
    }
}
