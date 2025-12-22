package com.datacrowd.core.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
public class TaskEntity {

    @Id
    @GeneratedValue
    private UUID id;

    // В БД: project_id uuid
    @Column(name = "project_id", columnDefinition = "uuid")
    private UUID projectId;

    // В БД: dataset_id uuid
    @Column(name = "dataset_id", columnDefinition = "uuid")
    private UUID datasetId;

    // связи (не обязательны, но удобно)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", referencedColumnName = "id", insertable = false, updatable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private DatasetEntity dataset;

    // ✅ В БД: batch_id uuid REFERENCES task_batches(id)
    @Column(name = "batch_id", columnDefinition = "uuid")
    private UUID batchId;

    // (опционально) связь на батч
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", referencedColumnName = "id", insertable = false, updatable = false)
    private TaskBatchEntity batch;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.NEW;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @CreationTimestamp
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getDatasetId() { return datasetId; }
    public void setDatasetId(UUID datasetId) { this.datasetId = datasetId; }

    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity project) {
        this.project = project;
        this.projectId = (project == null ? null : project.getId());
    }

    public DatasetEntity getDataset() { return dataset; }
    public void setDataset(DatasetEntity dataset) {
        this.dataset = dataset;
        this.datasetId = (dataset == null ? null : dataset.getId());
    }

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public TaskBatchEntity getBatch() { return batch; }
    public void setBatch(TaskBatchEntity batch) {
        this.batch = batch;
        this.batchId = (batch == null ? null : batch.getId());
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public Instant getCreatedAt() { return createdAt; }
}
