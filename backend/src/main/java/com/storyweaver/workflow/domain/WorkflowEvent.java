package com.storyweaver.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workflow_event")
public class WorkflowEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "workflow_run_id", nullable = false)
    private UUID workflowRunId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "step_name", nullable = false, length = 32)
    private String stepName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkflowEvent() {}

    public WorkflowEvent(
            UUID projectId,
            UUID workflowRunId,
            String eventType,
            String stepName,
            Map<String, Object> payload,
            Instant createdAt) {
        this.projectId = projectId;
        this.workflowRunId = workflowRunId;
        this.eventType = eventType;
        this.stepName = stepName;
        this.payload = new LinkedHashMap<>(payload);
        this.createdAt = createdAt;
    }

    public Long getEventId() {
        return eventId;
    }

    public UUID getWorkflowRunId() {
        return workflowRunId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStepName() {
        return stepName;
    }

    public Map<String, Object> getPayload() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
