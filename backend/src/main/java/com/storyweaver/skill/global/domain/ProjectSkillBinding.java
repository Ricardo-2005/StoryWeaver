package com.storyweaver.skill.global.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_skill_binding")
public class ProjectSkillBinding {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_type", nullable = false, length = 24)
    private ProjectSkillBindingType bindingType;

    @Column(name = "global_skill_id", nullable = false)
    private UUID globalSkillId;

    @Column(name = "global_skill_version_id", nullable = false)
    private UUID globalSkillVersionId;

    @Column(name = "snapshot_hash", nullable = false, length = 64)
    private String snapshotHash;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectSkillBinding() {}

    public ProjectSkillBinding(
            UUID projectId,
            UUID globalSkillId,
            UUID globalSkillVersionId,
            String snapshotHash,
            UUID createdBy,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.bindingType = ProjectSkillBindingType.FOUNDATION;
        this.globalSkillId = globalSkillId;
        this.globalSkillVersionId = globalSkillVersionId;
        this.snapshotHash = snapshotHash;
        this.enabled = true;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public ProjectSkillBindingType getBindingType() {
        return bindingType;
    }

    public UUID getGlobalSkillId() {
        return globalSkillId;
    }

    public UUID getGlobalSkillVersionId() {
        return globalSkillVersionId;
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
