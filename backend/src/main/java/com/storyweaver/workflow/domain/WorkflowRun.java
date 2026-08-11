package com.storyweaver.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_run")
public class WorkflowRun {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "viewpoint_character_id", nullable = false)
    private UUID viewpointCharacterId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false)
    private String instruction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkflowStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_json", columnDefinition = "jsonb")
    private Map<String, Object> plan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_json", columnDefinition = "jsonb")
    private Map<String, Object> extraction;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review_json", columnDefinition = "jsonb")
    private Map<String, Object> review;

    @Column(name = "draft_content", nullable = false)
    private String draftContent;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "recovery_count", nullable = false)
    private int recoveryCount;

    @Column(name = "revision_count", nullable = false)
    private int revisionCount;

    @Column(name = "committed_version_no")
    private Integer committedVersionNo;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkflowRun() {}

    public WorkflowRun(
            UUID projectId,
            UUID chapterId,
            UUID userId,
            UUID viewpointCharacterId,
            String idempotencyKey,
            String instruction,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.chapterId = chapterId;
        this.userId = userId;
        this.viewpointCharacterId = viewpointCharacterId;
        this.idempotencyKey = idempotencyKey;
        this.instruction = instruction;
        this.status = WorkflowStatus.CREATED;
        this.draftContent = "";
        this.heartbeatAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void transition(WorkflowStatus next, WorkflowStateMachine machine, Instant now) {
        machine.requireTransition(status, next);
        status = next;
        if (startedAt == null && next != WorkflowStatus.CREATED) startedAt = now;
        finishedAt = next.streamComplete() ? now : null;
        heartbeatAt = now;
        updatedAt = now;
    }

    public void plan(Map<String, Object> value, Instant now) {
        plan = new LinkedHashMap<>(value);
        heartbeat(now);
    }

    public void appendDraft(String text, Instant now) {
        draftContent += text;
        heartbeat(now);
    }

    public void resetDraftForRecovery(Instant now) {
        draftContent = "";
        recoveryCount++;
        heartbeat(now);
    }

    public void markRecovered(Instant now) {
        recoveryCount++;
        heartbeat(now);
    }

    public void extraction(Map<String, Object> value, Instant now) {
        extraction = new LinkedHashMap<>(value);
        heartbeat(now);
    }

    public void review(Map<String, Object> value, Instant now) {
        review = new LinkedHashMap<>(value);
        heartbeat(now);
    }

    public void revisedDraft(String value, Instant now) {
        draftContent = value;
        extraction = null;
        review = null;
        revisionCount++;
        failureCode = null;
        failureMessage = null;
        heartbeat(now);
    }

    public void committed(int versionNo, UUID userId, Instant now) {
        committedVersionNo = versionNo;
        approvedBy = userId;
        approvedAt = now;
        heartbeat(now);
    }

    public void requestCancellation(Instant now) {
        cancelRequested = true;
        heartbeat(now);
    }

    public void heartbeat(Instant now) {
        heartbeatAt = now;
        updatedAt = now;
    }

    public void failed(String code, String message, Instant now) {
        failureCode = code;
        failureMessage = message;
        heartbeat(now);
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

    public UUID getUserId() {
        return userId;
    }

    public UUID getViewpointCharacterId() {
        return viewpointCharacterId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getInstruction() {
        return instruction;
    }

    public WorkflowStatus getStatus() {
        return status;
    }

    public Map<String, Object> getPlan() {
        return plan == null ? null : immutableCopy(plan);
    }

    public Map<String, Object> getExtraction() {
        return extraction == null ? null : immutableCopy(extraction);
    }

    public Map<String, Object> getReview() {
        return review == null ? null : immutableCopy(review);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> value) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    public String getDraftContent() {
        return draftContent;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public int getRecoveryCount() {
        return recoveryCount;
    }

    public int getRevisionCount() {
        return revisionCount;
    }

    public Integer getCommittedVersionNo() {
        return committedVersionNo;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
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
