package com.storyweaver.workflow.api;

import com.storyweaver.character.domain.LifeStatus;
import com.storyweaver.consistency.domain.FactStatus;
import com.storyweaver.consistency.domain.ItemStatus;
import com.storyweaver.consistency.domain.KnowledgeCertainty;
import com.storyweaver.consistency.domain.ReviewSeverity;
import com.storyweaver.consistency.domain.ReviewSource;
import com.storyweaver.workflow.domain.WorkflowStatus;
import com.storyweaver.workflow.domain.WorkflowStepStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkflowDtos {
    private WorkflowDtos() {}

    public record StartWorkflowRequest(
            @NotNull UUID viewpointCharacterId, @NotBlank @Size(max = 20000) String instruction) {}

    public record RevisionRequest(@NotBlank @Size(max = 500000) String revisedDraft) {}

    public record LocalRevisionRequest(
            long expectedVersion,
            @jakarta.validation.constraints.Min(0) int startOffset,
            @jakarta.validation.constraints.Min(0) int endOffset,
            @NotNull @Size(max = 60000) String replacement,
            @NotBlank @Size(max = 500) String reason) {}

    public record ApproveWorkflowRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @Size(max = 500) String changeSummary,
            @NotNull List<@Min(0) Integer> acceptedFactIndexes,
            @NotNull List<@Valid CharacterStateChangeRequest> characterStateChanges,
            @NotNull List<@Valid ItemChangeRequest> itemChanges,
            @NotNull List<@Valid TimelineEventRequest> timelineEvents,
            @NotNull List<@Valid KnowledgeChangeRequest> knowledgeChanges) {}

    public record CharacterStateChangeRequest(
            @NotNull UUID characterId,
            @NotNull LifeStatus lifeStatus,
            @Size(max = 200) String currentLocation,
            @Size(max = 5000) String physicalCondition,
            @Size(max = 5000) String emotionalState,
            @Size(max = 10000) String abilities,
            @Size(max = 10000) String inventoryNotes,
            @Size(max = 10000) String notes,
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 5000) String evidence) {}

    public record ItemChangeRequest(
            @NotBlank @Size(max = 160) String itemKey,
            @NotBlank @Size(max = 200) String itemName,
            UUID fromOwnerCharacterId,
            UUID toOwnerCharacterId,
            @NotNull ItemStatus status,
            @NotBlank @Size(max = 5000) String evidence) {}

    public record TimelineEventRequest(
            @NotNull List<UUID> participantIds,
            @NotNull List<UUID> knownByIds,
            @Size(max = 200) String location,
            @Size(max = 200) String storyTime,
            @NotBlank @Size(max = 20000) String action,
            @NotBlank @Size(max = 20000) String result,
            @DecimalMin("0.0") @DecimalMax("1.0") double importance,
            @NotBlank @Size(max = 5000) String evidence) {}

    public record KnowledgeChangeRequest(
            @NotNull UUID characterId,
            @NotBlank @Size(max = 160) String factKey,
            @NotBlank @Size(max = 20000) String content,
            @NotNull KnowledgeCertainty certainty,
            UUID sourceEventId,
            @NotBlank @Size(max = 5000) String evidence) {}

    public record WorkflowResponse(
            UUID id,
            UUID projectId,
            UUID chapterId,
            UUID viewpointCharacterId,
            WorkflowStatus status,
            String draftContent,
            Map<String, Object> plan,
            Map<String, Object> extraction,
            Map<String, Object> review,
            boolean cancelRequested,
            int recoveryCount,
            int revisionCount,
            Integer committedVersionNo,
            UUID approvedBy,
            Instant approvedAt,
            String failureCode,
            String failureMessage,
            Instant heartbeatAt,
            Instant startedAt,
            Instant finishedAt,
            long version,
            Instant createdAt,
            Instant updatedAt,
            ContextPacketResponse contextPacket,
            List<WorkflowStepResponse> steps,
            List<StoryFactResponse> candidateFacts,
            List<ReviewIssueResponse> reviewIssues) {}

    public record StoryFactResponse(
            UUID id,
            int candidateIndex,
            String factKey,
            String content,
            String evidence,
            String paragraphKey,
            FactStatus status,
            Instant createdAt) {}

    public record ReviewIssueResponse(
            UUID id,
            ReviewSource source,
            String category,
            ReviewSeverity severity,
            String message,
            String evidence,
            String historicalEvidence,
            String suggestion,
            boolean blocking,
            boolean resolved,
            Instant createdAt) {}

    public record ContextPacketResponse(
            UUID id,
            int tokenEstimate,
            BigDecimal estimatedCost,
            Instant expiresAt,
            boolean stale,
            Instant createdAt) {}

    public record WorkflowStepResponse(
            UUID id,
            String stepName,
            WorkflowStepStatus status,
            int attempt,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt) {}

    public record WorkflowEventResponse(
            long eventId, UUID runId, String type, String step, Instant timestamp, Map<String, Object> payload) {}
}
