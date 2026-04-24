package com.datacrowd.core.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "failed_items")
public class FailedItemEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "raw_content", columnDefinition = "text")
    private String rawContent;

    @Column(name = "error_msg", nullable = false, columnDefinition = "text")
    private String errorMsg;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FailedItemEntity() {}

    public FailedItemEntity(UUID datasetId, int lineNumber,
                            String rawContent, String errorMsg) {
        this.datasetId   = datasetId;
        this.lineNumber  = lineNumber;
        this.rawContent  = rawContent;
        this.errorMsg    = errorMsg;
    }

    public UUID    getId()          { return id; }
    public UUID    getDatasetId()   { return datasetId; }
    public int     getLineNumber()  { return lineNumber; }
    public String  getRawContent()  { return rawContent; }
    public String  getErrorMsg()    { return errorMsg; }
    public Instant getCreatedAt()   { return createdAt; }
}