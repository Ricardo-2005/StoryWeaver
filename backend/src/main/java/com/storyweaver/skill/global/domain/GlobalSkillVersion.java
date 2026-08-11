package com.storyweaver.skill.global.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "global_skill_version")
public class GlobalSkillVersion {
    @Id
    private UUID id;

    @Column(name = "global_skill_id", nullable = false)
    private UUID globalSkillId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contract_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> contract;

    @Column(name = "snapshot_hash", nullable = false, length = 64)
    private String snapshotHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GlobalSkillStatus status;

    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GlobalSkillVersion() {}

    public GlobalSkillVersion(
            UUID globalSkillId,
            int versionNo,
            Map<String, Object> contract,
            String snapshotHash,
            GlobalSkillStatus status,
            int tokenEstimate,
            UUID createdBy,
            Instant now) {
        this.id = UUID.randomUUID();
        this.globalSkillId = globalSkillId;
        this.versionNo = versionNo;
        this.contract = new LinkedHashMap<>(contract);
        this.snapshotHash = snapshotHash;
        this.status = status;
        this.tokenEstimate = tokenEstimate;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGlobalSkillId() {
        return globalSkillId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public Map<String, Object> getContract() {
        return Map.copyOf(contract);
    }

    public String getSnapshotHash() {
        return snapshotHash;
    }

    public GlobalSkillStatus getStatus() {
        return status;
    }

    public int getTokenEstimate() {
        return tokenEstimate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
