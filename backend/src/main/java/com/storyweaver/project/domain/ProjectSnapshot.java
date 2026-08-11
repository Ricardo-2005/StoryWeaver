package com.storyweaver.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "project_snapshot")
public class ProjectSnapshot {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "project_version", nullable = false)
    private long projectVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectSnapshot() {}

    public ProjectSnapshot(
            UUID projectId, UUID createdBy, long projectVersion, Map<String, Object> snapshot, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.createdBy = createdBy;
        this.projectVersion = projectVersion;
        this.snapshot = Map.copyOf(snapshot);
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public long getProjectVersion() {
        return projectVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
