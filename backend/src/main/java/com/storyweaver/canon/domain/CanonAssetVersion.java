package com.storyweaver.canon.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "canon_asset_version")
public class CanonAssetVersion {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private String content;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CanonAssetVersion() {}

    public CanonAssetVersion(
            UUID projectId,
            UUID assetId,
            int versionNo,
            String name,
            String content,
            String changeSummary,
            UUID createdBy,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.assetId = assetId;
        this.versionNo = versionNo;
        this.name = name;
        this.content = content;
        this.changeSummary = changeSummary;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
