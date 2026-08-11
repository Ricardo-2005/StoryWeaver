package com.storyweaver.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skill_binding")
public class SkillBinding {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "skill_definition_id", nullable = false)
    private UUID skillDefinitionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SkillScope scope;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SkillBinding() {}

    public SkillBinding(
            UUID projectId, UUID skillDefinitionId, SkillScope scope, UUID chapterId, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.skillDefinitionId = skillDefinitionId;
        this.scope = scope;
        this.chapterId = chapterId;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public void rebind(SkillScope scope, UUID chapterId) {
        this.scope = scope;
        this.chapterId = chapterId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getSkillDefinitionId() {
        return skillDefinitionId;
    }

    public SkillScope getScope() {
        return scope;
    }

    public UUID getChapterId() {
        return chapterId;
    }
}
