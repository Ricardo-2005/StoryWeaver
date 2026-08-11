package com.storyweaver.consistency.domain;

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
@Table(name = "item_ownership")
public class ItemOwnership {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "item_key", nullable = false, length = 160)
    private String itemKey;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "owner_character_id")
    private UUID ownerCharacterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 16)
    private ItemStatus itemStatus;

    @Column(name = "acquired_chapter_id", nullable = false)
    private UUID acquiredChapterId;

    @Column(nullable = false)
    private String evidence;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ItemOwnership() {}

    public ItemOwnership(
            UUID projectId,
            String itemKey,
            String itemName,
            UUID ownerCharacterId,
            ItemStatus itemStatus,
            UUID acquiredChapterId,
            String evidence,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.itemKey = itemKey;
        this.createdAt = now;
        update(itemName, ownerCharacterId, itemStatus, acquiredChapterId, evidence, now);
    }

    public void update(
            String itemName,
            UUID ownerCharacterId,
            ItemStatus itemStatus,
            UUID acquiredChapterId,
            String evidence,
            Instant now) {
        this.itemName = itemName;
        this.ownerCharacterId = ownerCharacterId;
        this.itemStatus = itemStatus;
        this.acquiredChapterId = acquiredChapterId;
        this.evidence = evidence;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getItemKey() {
        return itemKey;
    }

    public String getItemName() {
        return itemName;
    }

    public UUID getOwnerCharacterId() {
        return ownerCharacterId;
    }

    public ItemStatus getItemStatus() {
        return itemStatus;
    }

    public UUID getAcquiredChapterId() {
        return acquiredChapterId;
    }

    public String getEvidence() {
        return evidence;
    }

    public long getVersion() {
        return version;
    }
}
