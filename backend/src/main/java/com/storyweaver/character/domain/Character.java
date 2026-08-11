package com.storyweaver.character.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "character")
public class Character {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String aliases;

    @Column(length = 80)
    private String role;

    private String description;
    private String personality;
    private String background;
    private String goals;
    private String appearance;
    private String notes;

    @Column(nullable = false)
    private boolean archived;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Character() {}

    public Character(UUID projectId, String name, Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.name = name;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String name,
            String aliases,
            String role,
            String description,
            String personality,
            String background,
            String goals,
            String appearance,
            String notes,
            boolean archived,
            Instant now) {
        this.name = name;
        this.aliases = aliases;
        this.role = role;
        this.description = description;
        this.personality = personality;
        this.background = background;
        this.goals = goals;
        this.appearance = appearance;
        this.notes = notes;
        this.archived = archived;
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

    public String getAliases() {
        return aliases;
    }

    public String getRole() {
        return role;
    }

    public String getDescription() {
        return description;
    }

    public String getPersonality() {
        return personality;
    }

    public String getBackground() {
        return background;
    }

    public String getGoals() {
        return goals;
    }

    public String getAppearance() {
        return appearance;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isArchived() {
        return archived;
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
