package com.storyweaver.outline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outline_node")
public class OutlineNode {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 20)
    private OutlineNodeType nodeType;

    @Column(nullable = false, length = 160)
    private String title;

    private String summary;
    private String objective;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OutlineNode() {}

    public OutlineNode(
            UUID projectId,
            UUID parentId,
            OutlineNodeType nodeType,
            String title,
            String summary,
            String objective,
            int sequenceNo,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.parentId = parentId;
        this.nodeType = nodeType;
        this.title = title;
        this.summary = summary;
        this.objective = objective;
        this.sequenceNo = sequenceNo;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, String summary, String objective, int sequenceNo, Instant now) {
        this.title = title;
        this.summary = summary;
        this.objective = objective;
        this.sequenceNo = sequenceNo;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public OutlineNodeType getNodeType() {
        return nodeType;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getObjective() {
        return objective;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
