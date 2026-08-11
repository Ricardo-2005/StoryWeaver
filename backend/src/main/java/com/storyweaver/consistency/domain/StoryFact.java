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
@Table(name = "story_fact")
public class StoryFact {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "workflow_run_id")
    private UUID workflowRunId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "candidate_index", nullable = false)
    private int candidateIndex;

    @Column(name = "fact_key", nullable = false, length = 160)
    private String factKey;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private String evidence;

    @Column(name = "paragraph_key", nullable = false, length = 120)
    private String paragraphKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FactStatus status;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "mcp_request_key", length = 160)
    private String mcpRequestKey;

    protected StoryFact() {}

    public StoryFact(
            UUID projectId,
            UUID workflowRunId,
            UUID chapterId,
            int candidateIndex,
            String factKey,
            String content,
            String evidence,
            String paragraphKey,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.workflowRunId = workflowRunId;
        this.chapterId = chapterId;
        this.candidateIndex = candidateIndex;
        this.factKey = factKey;
        this.content = content;
        this.evidence = evidence;
        this.paragraphKey = paragraphKey;
        this.status = FactStatus.CANDIDATE;
        this.source = "WORKFLOW";
        this.createdAt = now;
    }

    public static StoryFact mcpCandidate(
            UUID projectId,
            UUID createdBy,
            String factKey,
            String content,
            String evidence,
            String paragraphKey,
            String requestKey,
            Instant now) {
        StoryFact fact = new StoryFact();
        fact.id = UUID.randomUUID();
        fact.projectId = projectId;
        fact.candidateIndex = 0;
        fact.factKey = factKey;
        fact.content = content;
        fact.evidence = evidence;
        fact.paragraphKey = paragraphKey;
        fact.status = FactStatus.CANDIDATE;
        fact.source = "MCP";
        fact.createdBy = createdBy;
        fact.mcpRequestKey = requestKey;
        fact.createdAt = now;
        return fact;
    }

    public void decide(boolean accepted, UUID userId, Instant now) {
        status = accepted ? FactStatus.ACCEPTED : FactStatus.REJECTED;
        acceptedBy = accepted ? userId : null;
        acceptedAt = accepted ? now : null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getWorkflowRunId() {
        return workflowRunId;
    }

    public UUID getChapterId() {
        return chapterId;
    }

    public int getCandidateIndex() {
        return candidateIndex;
    }

    public String getFactKey() {
        return factKey;
    }

    public String getContent() {
        return content;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getParagraphKey() {
        return paragraphKey;
    }

    public FactStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getSource() {
        return source;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
