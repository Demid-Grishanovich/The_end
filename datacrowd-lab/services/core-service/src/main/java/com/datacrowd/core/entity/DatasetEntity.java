package com.datacrowd.core.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "datasets")
public class DatasetEntity {

    @Id
    @GeneratedValue
    private UUID id;

    // В БД: project_id uuid NOT NULL
    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    // Чтобы не было "project_id referred to by project_id/projectId"
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", referencedColumnName = "id", insertable = false, updatable = false)
    private ProjectEntity project;

    // В БД: name varchar NOT NULL
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    // В БД: source_path text (добавлено V2)
    @Column(name = "source_path", columnDefinition = "text")
    private String sourcePath;

    // В БД: status varchar NOT NULL DEFAULT 'NEW' (V2)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DatasetStatus status = DatasetStatus.UPLOADED;

    // В БД: total_items int NOT NULL DEFAULT 0 (V2)
    @Column(name = "total_items", nullable = false)
    private int totalItems = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ===== getters / setters =====
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public ProjectEntity getProject() { return project; }
    public void setProject(ProjectEntity project) {
        this.project = project;
        this.projectId = (project == null ? null : project.getId());
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    public DatasetStatus getStatus() { return status; }
    public void setStatus(DatasetStatus status) { this.status = status; }

    public int getTotalItems() { return totalItems; }
    public void setTotalItems(int totalItems) { this.totalItems = totalItems; }

    public Instant getCreatedAt() { return createdAt; }
}
