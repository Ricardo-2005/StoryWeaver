package com.storyweaver.canon.domain;

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
@Table(name = "canon_asset")
public class CanonAsset {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "asset_type", nullable = false, length = 40)
    private String assetType;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanonStatus status;

    @Column(name = "current_version_no", nullable = false)
    private int currentVersionNo;

    @Column(name = "confirmed_version_no")
    private Integer confirmedVersionNo;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CanonAsset() {}

    public CanonAsset(UUID projectId, String assetType, String name, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.assetType = assetType;
        this.name = name;
        this.status = CanonStatus.DRAFT;
        this.currentVersionNo = 1;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public int revise(String name, Instant now) {
        this.name = name;
        this.currentVersionNo += 1;
        this.status = CanonStatus.DRAFT;
        this.updatedAt = now;
        return currentVersionNo;
    }

    public void confirm(Instant now) {
        this.status = CanonStatus.CONFIRMED;
        this.confirmedVersionNo = currentVersionNo;
        this.updatedAt = now;
    }

    public void deprecate(Instant now) {
        this.status = CanonStatus.DEPRECATED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getAssetType() {
        return assetType;
    }

    public String getName() {
        return name;
    }

    public CanonStatus getStatus() {
        return status;
    }

    public int getCurrentVersionNo() {
        return currentVersionNo;
    }

    public Integer getConfirmedVersionNo() {
        return confirmedVersionNo;
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
