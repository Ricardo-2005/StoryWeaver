package com.storyweaver.consistency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_issue")
public class ReviewIssue {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "workflow_run_id", nullable = false)
    private UUID workflowRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewSource source;

    @Column(nullable = false, length = 40)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReviewSeverity severity;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private String evidence;

    @Column(name = "historical_evidence")
    private String historicalEvidence;

    @Column(nullable = false)
    private String suggestion;

    @Column(nullable = false)
    private boolean blocking;

    @Column(nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewIssue() {}

    public ReviewIssue(
            UUID projectId,
            UUID workflowRunId,
            ReviewSource source,
            String category,
            ReviewSeverity severity,
            String message,
            String evidence,
            String historicalEvidence,
            String suggestion,
            boolean blocking,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.workflowRunId = workflowRunId;
        this.source = source;
        this.category = category;
        this.severity = severity;
        this.message = message;
        this.evidence = evidence;
        this.historicalEvidence = historicalEvidence;
        this.suggestion = suggestion;
        this.blocking = blocking || severity == ReviewSeverity.BLOCKER;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkflowRunId() {
        return workflowRunId;
    }

    public ReviewSource getSource() {
        return source;
    }

    public String getCategory() {
        return category;
    }

    public ReviewSeverity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getHistoricalEvidence() {
        return historicalEvidence;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public boolean isBlocking() {
        return blocking;
    }

    public boolean isResolved() {
        return resolved;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
