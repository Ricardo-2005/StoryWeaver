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
@Table(name = "skill_forge_run")
public class SkillForgeRun {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "global_skill_id", nullable = false)
    private UUID globalSkillId;

    @Column(nullable = false, length = 24)
    private String mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ForgeRunStatus status;

    @Column(name = "source_material", nullable = false)
    private String sourceMaterial;

    @Column(name = "skill_type", nullable = false, length = 20)
    private String skillType;

    @Column(name = "material_tag", nullable = false, length = 20)
    private String materialTag;

    @Column(length = 80)
    private String genre;

    @Column(name = "source_project_id")
    private UUID sourceProjectId;

    @Column(name = "learning_focus", length = 1000)
    private String learningFocus;

    @Column(name = "material_description", length = 1000)
    private String materialDescription;

    @Column(name = "exclude_character_names", nullable = false)
    private boolean excludeCharacterNames;

    @Column(name = "exclude_locations", nullable = false)
    private boolean excludeLocations;

    @Column(name = "exclude_plot_facts", nullable = false)
    private boolean excludePlotFacts;

    @Column(name = "reusable_methods_only", nullable = false)
    private boolean reusableMethodsOnly;

    @Column(name = "ownership_statement", nullable = false)
    private String ownershipStatement;

    @Column(name = "ownership_confirmed_at")
    private Instant ownershipConfirmedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_contract", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> candidateContract;

    @Column
    private String summary;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillForgeRun() {}

    public SkillForgeRun(
            UUID ownerId,
            UUID globalSkillId,
            String skillType,
            String materialTag,
            String genre,
            UUID sourceProjectId,
            String learningFocus,
            String materialDescription,
            boolean excludeCharacterNames,
            boolean excludeLocations,
            boolean excludePlotFacts,
            boolean reusableMethodsOnly,
            String ownershipStatement,
            Map<String, Object> candidateContract,
            Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.globalSkillId = globalSkillId;
        this.mode = "TEXT_SOURCES";
        this.status = ForgeRunStatus.CREATED;
        this.sourceMaterial = "";
        this.skillType = skillType;
        this.materialTag = materialTag;
        this.genre = genre;
        this.sourceProjectId = sourceProjectId;
        this.learningFocus = learningFocus;
        this.materialDescription = materialDescription;
        this.excludeCharacterNames = excludeCharacterNames;
        this.excludeLocations = excludeLocations;
        this.excludePlotFacts = excludePlotFacts;
        this.reusableMethodsOnly = reusableMethodsOnly;
        this.ownershipStatement = ownershipStatement;
        this.ownershipConfirmedAt = now;
        this.candidateContract = new LinkedHashMap<>(candidateContract);
        this.summary = "熔炼任务已创建，等待添加 TXT 或手写文本。";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void transition(ForgeRunStatus status, String summary, Instant now) {
        this.status = status;
        this.summary = summary;
        this.updatedAt = now;
    }

    public void replaceCandidate(Map<String, Object> contract, String summary, Instant now) {
        this.candidateContract = new LinkedHashMap<>(contract);
        this.status = ForgeRunStatus.WAITING_REVIEW;
        this.summary = summary;
        this.updatedAt = now;
    }

    public void validate(boolean valid, String summary, Instant now) {
        this.status = valid ? ForgeRunStatus.VALIDATED : ForgeRunStatus.VALIDATION_FAILED;
        this.summary = summary;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = ForgeRunStatus.CANCELLED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getGlobalSkillId() {
        return globalSkillId;
    }

    public String getMode() {
        return mode;
    }

    public ForgeRunStatus getStatus() {
        return status;
    }

    public String getSourceMaterial() {
        return sourceMaterial;
    }

    public String getSkillType() {
        return skillType;
    }

    public String getMaterialTag() {
        return materialTag;
    }

    public String getGenre() {
        return genre;
    }

    public UUID getSourceProjectId() {
        return sourceProjectId;
    }

    public String getLearningFocus() {
        return learningFocus;
    }

    public String getMaterialDescription() {
        return materialDescription;
    }

    public boolean isExcludeCharacterNames() {
        return excludeCharacterNames;
    }

    public boolean isExcludeLocations() {
        return excludeLocations;
    }

    public boolean isExcludePlotFacts() {
        return excludePlotFacts;
    }

    public boolean isReusableMethodsOnly() {
        return reusableMethodsOnly;
    }

    public String getOwnershipStatement() {
        return ownershipStatement;
    }

    public Instant getOwnershipConfirmedAt() {
        return ownershipConfirmedAt;
    }

    public Map<String, Object> getCandidateContract() {
        return Map.copyOf(candidateContract);
    }

    public String getSummary() {
        return summary;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
