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
@Table(name = "character_state")
public class CharacterState {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "character_id", nullable = false)
    private UUID characterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "life_status", nullable = false, length = 16)
    private LifeStatus lifeStatus;

    @Column(name = "current_location", length = 200)
    private String currentLocation;

    @Column(name = "physical_condition")
    private String physicalCondition;

    @Column(name = "emotional_state")
    private String emotionalState;

    private String abilities;

    @Column(name = "inventory_notes")
    private String inventoryNotes;

    private String notes;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CharacterState() {}

    public CharacterState(UUID projectId, UUID characterId, Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.characterId = characterId;
        this.lifeStatus = LifeStatus.UNKNOWN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            LifeStatus lifeStatus,
            String currentLocation,
            String physicalCondition,
            String emotionalState,
            String abilities,
            String inventoryNotes,
            String notes,
            Instant now) {
        this.lifeStatus = lifeStatus;
        this.currentLocation = currentLocation;
        this.physicalCondition = physicalCondition;
        this.emotionalState = emotionalState;
        this.abilities = abilities;
        this.inventoryNotes = inventoryNotes;
        this.notes = notes;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public LifeStatus getLifeStatus() {
        return lifeStatus;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public String getPhysicalCondition() {
        return physicalCondition;
    }

    public String getEmotionalState() {
        return emotionalState;
    }

    public String getAbilities() {
        return abilities;
    }

    public String getInventoryNotes() {
        return inventoryNotes;
    }

    public String getNotes() {
        return notes;
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
