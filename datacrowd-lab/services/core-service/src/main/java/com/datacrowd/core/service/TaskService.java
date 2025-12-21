package com.datacrowd.core.service;

import com.datacrowd.core.entity.DatasetEntity;
import com.datacrowd.core.entity.ProjectEntity;
import com.datacrowd.core.entity.TaskEntity;
import com.datacrowd.core.entity.TaskStatus;
import com.datacrowd.core.repo.DatasetRepository;
import com.datacrowd.core.repo.ProjectRepository;
import com.datacrowd.core.repo.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TaskService {

    private final ProjectRepository projectRepository;
    private final DatasetRepository datasetRepository;
    private final TaskRepository taskRepository;

    public TaskService(ProjectRepository projectRepository, DatasetRepository datasetRepository, TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.datasetRepository = datasetRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public int generateTasks(UUID projectId, UUID datasetId, int count) {
        if (count <= 0 || count > 1000) {
            throw new IllegalArgumentException("count must be 1..1000");
        }

        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        DatasetEntity dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));

        // защита от несоответствия project/dataset
        if (!dataset.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("Dataset does not belong to project");
        }

        for (int i = 1; i <= count; i++) {
            TaskEntity t = new TaskEntity();
            t.setProject(project);
            t.setDataset(dataset);
            t.setTitle("Task " + i + " for dataset " + dataset.getName());
            t.setDescription("Auto-generated task");
            t.setStatus(TaskStatus.OPEN);

            taskRepository.save(t);
        }

        return count;
    }
}
