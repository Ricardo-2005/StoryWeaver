package com.storyweaver.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "skill_definition")
public class SkillDefinition {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> rules;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillDefinition() {}

    public SkillDefinition(
            UUID projectId,
            String name,
            String description,
            Map<String, String> rules,
            boolean enabled,
            UUID createdBy,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.name = name;
        this.description = description;
        this.rules = new LinkedHashMap<>(rules);
        this.enabled = enabled;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String description, Map<String, String> rules, boolean enabled, Instant now) {
        this.name = name;
        this.description = description;
        this.rules = new LinkedHashMap<>(rules);
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getRules() {
        return Map.copyOf(rules);
    }

    public boolean isEnabled() {
        return enabled;
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
