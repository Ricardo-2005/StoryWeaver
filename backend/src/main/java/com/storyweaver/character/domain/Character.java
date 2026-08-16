package com.storyweaver.character.domain;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CharacterImportance importance;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private CharacterLifecycleStatus lifecycleStatus;

    @Column(name = "merged_into")
    private UUID mergedInto;

    @Column(name = "retrieval_eligible", nullable = false)
    private boolean retrievalEligible;

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
        this.importance = CharacterImportance.MINOR;
        this.lifecycleStatus = CharacterLifecycleStatus.ACTIVE;
        this.retrievalEligible = true;
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
        if (archived) {
            transition(CharacterLifecycleStatus.ARCHIVED, null, now);
        } else if (lifecycleStatus == CharacterLifecycleStatus.ARCHIVED) {
            transition(CharacterLifecycleStatus.ACTIVE, null, now);
        }
        this.updatedAt = now;
    }

    public void transition(CharacterLifecycleStatus lifecycleStatus, UUID mergedInto, Instant now) {
        if (lifecycleStatus == CharacterLifecycleStatus.MERGED && mergedInto == null) {
            throw new IllegalArgumentException("Merged character requires a canonical target");
        }
        if (lifecycleStatus != CharacterLifecycleStatus.MERGED && mergedInto != null) {
            throw new IllegalArgumentException("Only merged characters can have a canonical target");
        }
        this.lifecycleStatus = lifecycleStatus;
        this.mergedInto = mergedInto;
        this.archived = lifecycleStatus == CharacterLifecycleStatus.ARCHIVED;
        this.retrievalEligible = lifecycleStatus.historicalRetrievalEligible();
        this.updatedAt = now;
    }

    public void importance(CharacterImportance importance, Instant now) {
        this.importance = importance;
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

    public CharacterImportance getImportance() {
        return importance;
    }

    public CharacterLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public UUID getMergedInto() {
        return mergedInto;
    }

    public boolean isRetrievalEligible() {
        return retrievalEligible;
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
