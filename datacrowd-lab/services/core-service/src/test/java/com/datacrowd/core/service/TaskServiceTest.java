package com.datacrowd.core.service;

import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.entity.TaskStatus;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.repo.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock ProjectRepository projectRepository;
    @Mock DatasetRepository datasetRepository;
    @Mock TaskRepository taskRepository;

    @InjectMocks TaskService taskService;

    @Captor ArgumentCaptor<TaskEntity> taskCaptor;

    UUID projectId;
    UUID datasetId;
    ProjectEntity project;
    DatasetEntity dataset;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        datasetId = UUID.randomUUID();

        project = new ProjectEntity();
        project.setId(projectId);
        project.setName("P1");

        dataset = new DatasetEntity();
        dataset.setId(datasetId);
        dataset.setName("D1");
        dataset.setProject(project);
    }

    @Test
    void generateTasks_savesExactlyCountTasks_andReturnsCount() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        int result = taskService.generateTasks(projectId, datasetId, 3);

        assertThat(result).isEqualTo(3);
        verify(taskRepository, times(3)).save(taskCaptor.capture());

        assertThat(taskCaptor.getAllValues())
                .hasSize(3)
                .allSatisfy(t -> {
                    assertThat(t.getProject()).isSameAs(project);
                    assertThat(t.getDataset()).isSameAs(dataset);
                    assertThat(t.getStatus()).isEqualTo(TaskStatus.OPEN);
                    assertThat(t.getDescription()).isEqualTo("Auto-generated task");
                });

        assertThat(taskCaptor.getAllValues().get(0).getTitle()).isEqualTo("Task 1 for dataset D1");
        assertThat(taskCaptor.getAllValues().get(1).getTitle()).isEqualTo("Task 2 for dataset D1");
        assertThat(taskCaptor.getAllValues().get(2).getTitle()).isEqualTo("Task 3 for dataset D1");
    }

    @Test
    void generateTasks_throwsWhenCountOutOfRange() {
        assertThatThrownBy(() -> taskService.generateTasks(projectId, datasetId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count must be 1..1000");

        assertThatThrownBy(() -> taskService.generateTasks(projectId, datasetId, 1001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count must be 1..1000");

        verifyNoInteractions(projectRepository, datasetRepository, taskRepository);
    }

    @Test
    void generateTasks_throwsWhenProjectNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.generateTasks(projectId, datasetId, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");

        verify(projectRepository).findById(projectId);
        verifyNoInteractions(datasetRepository, taskRepository);
    }

    @Test
    void generateTasks_throwsWhenDatasetNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.generateTasks(projectId, datasetId, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dataset not found");

        verify(projectRepository).findById(projectId);
        verify(datasetRepository).findById(datasetId);
        verifyNoInteractions(taskRepository);
    }

    @Test
    void generateTasks_throwsWhenDatasetDoesNotBelongToProject() {
        ProjectEntity otherProject = new ProjectEntity();
        otherProject.setId(UUID.randomUUID());

        dataset.setProject(otherProject);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(datasetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));

        assertThatThrownBy(() -> taskService.generateTasks(projectId, datasetId, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dataset does not belong to project");

        verifyNoInteractions(taskRepository);
    }
}
