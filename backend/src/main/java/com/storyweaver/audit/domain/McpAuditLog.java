package com.storyweaver.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_audit_log")
public class McpAuditLog {
    @Id
    private UUID id;

    @Column(name = "caller_user_id", nullable = false)
    private UUID callerUserId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "operation_type", nullable = false, length = 16)
    private String operationType;

    @Column(name = "operation_name", nullable = false, length = 120)
    private String operationName;

    @Column(name = "request_id", nullable = false, length = 160)
    private String requestId;

    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "duration_ms", nullable = false)
    private long durationMillis;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected McpAuditLog() {}

    public McpAuditLog(
            UUID callerUserId,
            UUID projectId,
            String operationType,
            String operationName,
            String requestId,
            String outcome,
            String errorCode,
            long durationMillis,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.callerUserId = callerUserId;
        this.projectId = projectId;
        this.operationType = operationType;
        this.operationName = operationName;
        this.requestId = requestId;
        this.outcome = outcome;
        this.errorCode = errorCode;
        this.durationMillis = durationMillis;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCallerUserId() {
        return callerUserId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
