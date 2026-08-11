package com.storyweaver.skill.global.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "global_skill")
public class GlobalSkill {
    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GlobalSkillScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GlobalSkillStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contract_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> contract;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GlobalSkill() {}

    public GlobalSkill(
            UUID ownerId,
            String slug,
            String displayName,
            String description,
            GlobalSkillScope scope,
            GlobalSkillStatus status,
            Map<String, Object> contract,
            Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.slug = slug;
        this.displayName = displayName;
        this.description = description;
        this.scope = scope;
        this.status = status;
        this.contract = new LinkedHashMap<>(contract);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void publishVersion(UUID versionId, Map<String, Object> contract, GlobalSkillStatus status, Instant now) {
        this.currentVersionId = versionId;
        this.contract = new LinkedHashMap<>(contract);
        this.status = status;
        this.updatedAt = now;
    }

    public void replaceDraftContract(Map<String, Object> contract, Instant now) {
        this.contract = new LinkedHashMap<>(contract);
        this.status = GlobalSkillStatus.WAITING_REVIEW;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.status = GlobalSkillStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public GlobalSkillScope getScope() {
        return scope;
    }

    public GlobalSkillStatus getStatus() {
        return status;
    }

    public Map<String, Object> getContract() {
        return Map.copyOf(contract);
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
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
