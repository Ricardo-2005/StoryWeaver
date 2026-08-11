package com.storyweaver.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_step")
public class WorkflowStep {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "workflow_run_id", nullable = false)
    private UUID workflowRunId;

    @Column(name = "step_name", nullable = false, length = 32)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkflowStepStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected WorkflowStep() {}

    public WorkflowStep(UUID projectId, UUID workflowRunId, String stepName, Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.workflowRunId = workflowRunId;
        this.stepName = stepName;
        this.status = WorkflowStepStatus.RUNNING;
        this.attempt = 1;
        this.startedAt = now;
    }

    public void restart(Instant now) {
        status = WorkflowStepStatus.RUNNING;
        attempt++;
        errorCode = null;
        errorMessage = null;
        startedAt = now;
        finishedAt = null;
    }

    public void complete(Instant now) {
        status = WorkflowStepStatus.COMPLETED;
        finishedAt = now;
    }

    public void fail(String code, String message, Instant now) {
        status = WorkflowStepStatus.FAILED;
        errorCode = code;
        errorMessage = message;
        finishedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkflowRunId() {
        return workflowRunId;
    }

    public String getStepName() {
        return stepName;
    }

    public WorkflowStepStatus getStatus() {
        return status;
    }

    public int getAttempt() {
        return attempt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
