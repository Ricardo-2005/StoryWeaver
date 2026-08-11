package com.storyweaver.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "context_packet")
public class ContextPacket {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "workflow_run_id", nullable = false, unique = true)
    private UUID workflowRunId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> contextData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "worldbook_report", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> worldbookReport;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "memory_report", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> memoryReport;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> skillSnapshot;

    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;

    @Column(name = "estimated_cost", nullable = false, precision = 16, scale = 6)
    private BigDecimal estimatedCost;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContextPacket() {}

    public ContextPacket(
            UUID projectId,
            UUID chapterId,
            UUID workflowRunId,
            UUID createdBy,
            Map<String, Object> contextData,
            Map<String, Object> worldbookReport,
            Map<String, Object> memoryReport,
            Map<String, Object> skillSnapshot,
            int tokenEstimate,
            BigDecimal estimatedCost,
            Instant expiresAt,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.chapterId = chapterId;
        this.workflowRunId = workflowRunId;
        this.createdBy = createdBy;
        this.contextData = new LinkedHashMap<>(contextData);
        this.worldbookReport = new LinkedHashMap<>(worldbookReport);
        this.memoryReport = new LinkedHashMap<>(memoryReport);
        this.skillSnapshot = new LinkedHashMap<>(skillSnapshot);
        this.tokenEstimate = tokenEstimate;
        this.estimatedCost = estimatedCost;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean isStale(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public UUID getWorkflowRunId() {
        return workflowRunId;
    }

    public Map<String, Object> getContextData() {
        return immutableCopy(contextData);
    }

    public Map<String, Object> getWorldbookReport() {
        return immutableCopy(worldbookReport);
    }

    public Map<String, Object> getMemoryReport() {
        return immutableCopy(memoryReport);
    }

    public Map<String, Object> getSkillSnapshot() {
        return immutableCopy(skillSnapshot);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    public int getTokenEstimate() {
        return tokenEstimate;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
