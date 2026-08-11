package com.storyweaver.workflow.application;

import com.storyweaver.chapter.domain.Chapter;
import com.storyweaver.chapter.repository.ChapterRepository;
import com.storyweaver.consistency.application.ConsistencyModels.CharacterStateChange;
import com.storyweaver.consistency.application.ConsistencyModels.CommitProposal;
import com.storyweaver.consistency.application.ConsistencyModels.ItemChange;
import com.storyweaver.consistency.application.ConsistencyModels.KnowledgeChange;
import com.storyweaver.consistency.application.ConsistencyModels.TimelineEvent;
import com.storyweaver.consistency.application.ConsistencyReviewService;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.workflow.api.WorkflowDtos.ApproveWorkflowRequest;
import com.storyweaver.workflow.api.WorkflowDtos.ContextPacketResponse;
import com.storyweaver.workflow.api.WorkflowDtos.ReviewIssueResponse;
import com.storyweaver.workflow.api.WorkflowDtos.StoryFactResponse;
import com.storyweaver.workflow.api.WorkflowDtos.WorkflowEventResponse;
import com.storyweaver.workflow.api.WorkflowDtos.WorkflowResponse;
import com.storyweaver.workflow.api.WorkflowDtos.WorkflowStepResponse;
import com.storyweaver.workflow.config.WorkflowProperties;
import com.storyweaver.workflow.domain.ContextPacket;
import com.storyweaver.workflow.domain.WorkflowEvent;
import com.storyweaver.workflow.domain.WorkflowRun;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class WorkflowService {
    private final WorkflowStore store;
    private final WorkflowOrchestrator orchestrator;
    private final WorkflowApprovalService approval;
    private final ConsistencyReviewService reviews;
    private final ChapterRepository chapters;
    private final ProjectAccessService projectAccess;
    private final WorkflowProperties properties;
    private final ExecutorService executor;
    private final Clock clock;
    private final AtomicInteger activeStreams = new AtomicInteger();
    private final ApplicationEventPublisher events;

    public WorkflowService(
            WorkflowStore store,
            WorkflowOrchestrator orchestrator,
            WorkflowApprovalService approval,
            ConsistencyReviewService reviews,
            ChapterRepository chapters,
            ProjectAccessService projectAccess,
            WorkflowProperties properties,
            ExecutorService aiTaskExecutor,
            MeterRegistry meters,
            Clock clock,
            ApplicationEventPublisher events) {
        this.store = store;
        this.orchestrator = orchestrator;
        this.approval = approval;
        this.reviews = reviews;
        this.chapters = chapters;
        this.projectAccess = projectAccess;
        this.properties = properties;
        this.executor = aiTaskExecutor;
        this.clock = clock;
        this.events = events;
        meters.gauge("storyweaver.sse.connections", activeStreams);
    }

    public WorkflowResponse start(
            UUID chapterId, UUID userId, String idempotencyKey, UUID viewpointCharacterId, String instruction) {
        validateIdempotencyKey(idempotencyKey);
        Chapter chapter = chapters.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("chapter_not_found", "Chapter was not found"));
        projectAccess.requireOwnedProject(chapter.getProjectId(), userId);
        var result = store.create(
                chapter.getProjectId(), chapterId, userId, viewpointCharacterId, idempotencyKey, instruction.trim());
        if (result.created()) orchestrator.submit(result.run().getId());
        return response(result.run().getId(), userId);
    }

    public WorkflowResponse get(UUID runId, UUID userId) {
        return response(store.requireOwned(runId, userId).getId(), userId);
    }

    public WorkflowResponse cancel(UUID runId, UUID userId) {
        store.cancel(runId, userId);
        return response(runId, userId);
    }

    public WorkflowResponse reextract(UUID runId, UUID userId, String revisedDraft) {
        store.revise(runId, userId, revisedDraft.strip());
        reviews.clearReviewArtifacts(runId);
        orchestrator.submit(runId);
        return response(runId, userId);
    }

    public WorkflowResponse approve(UUID runId, UUID userId, ApproveWorkflowRequest request) {
        approval.approve(runId, userId, request.expectedVersion(), request.changeSummary(), proposal(request));
        WorkflowRun run = store.requireOwned(runId, userId);
        events.publishEvent(new WorkflowApprovedEvent(run.getId(), run.getProjectId(), run.getChapterId(), userId));
        return response(runId, userId);
    }

    public WorkflowResponse localRevision(
            UUID runId,
            UUID userId,
            long expectedVersion,
            int startOffset,
            int endOffset,
            String replacement,
            String reason) {
        WorkflowRun run = store.requireOwned(runId, userId);
        if (run.getVersion() != expectedVersion) {
            throw new com.storyweaver.shared.error.ConflictException(
                    "stale_version", "Workflow was changed by another request");
        }
        String draft = run.getDraftContent();
        if (startOffset < 0 || endOffset < startOffset || endOffset > draft.length()) {
            throw new BadRequestException("local_revision_range_invalid", "Local revision offsets are invalid");
        }
        int changed = Math.max(endOffset - startOffset, replacement.length());
        double ratio = draft.isEmpty() ? (changed == 0 ? 0 : 1) : (double) changed / draft.length();
        if (ratio > 0.15d) {
            throw new BadRequestException(
                    "local_revision_scope_exceeded", "Local revision may change at most 15% of the draft");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("local_revision_reason_required", "Local revision reason is required");
        }
        String revised = draft.substring(0, startOffset) + replacement + draft.substring(endOffset);
        return reextract(runId, userId, revised);
    }

    public SseEmitter events(UUID runId, UUID userId, long afterEventId) {
        store.requireOwned(runId, userId);
        if (afterEventId < 0) throw new BadRequestException("invalid_event_id", "Event ID must not be negative");
        SseEmitter emitter = new SseEmitter(properties.eventStreamTimeout().toMillis());
        AtomicBoolean closed = new AtomicBoolean();
        activeStreams.incrementAndGet();
        emitter.onCompletion(() -> close(closed));
        emitter.onTimeout(() -> close(closed));
        emitter.onError(ignored -> close(closed));
        executor.submit(() -> stream(runId, afterEventId, emitter, closed));
        return emitter;
    }

    private void stream(UUID runId, long afterEventId, SseEmitter emitter, AtomicBoolean closed) {
        long cursor = afterEventId;
        long lastHeartbeat = clock.millis();
        try {
            while (!closed.get()) {
                List<WorkflowEvent> batch = store.eventsAfter(runId, cursor);
                for (WorkflowEvent event : batch) {
                    send(emitter, event);
                    cursor = event.getEventId();
                }
                WorkflowRun run = store.require(runId);
                if (run.getStatus().streamComplete() && batch.isEmpty()) {
                    emitter.complete();
                    return;
                }
                if (clock.millis() - lastHeartbeat
                        >= properties.heartbeatInterval().toMillis()) {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("runId", runId, "timestamp", clock.instant())));
                    lastHeartbeat = clock.millis();
                }
                Thread.sleep(properties.eventPollInterval().toMillis());
            }
        } catch (IOException | InterruptedException | IllegalStateException exception) {
            if (exception instanceof InterruptedException)
                Thread.currentThread().interrupt();
            close(closed);
        }
    }

    private void close(AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) activeStreams.decrementAndGet();
    }

    private void send(SseEmitter emitter, WorkflowEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(event.getEventId()))
                .name(event.getEventType())
                .data(new WorkflowEventResponse(
                        event.getEventId(),
                        event.getWorkflowRunId(),
                        event.getEventType(),
                        event.getStepName(),
                        event.getCreatedAt(),
                        event.getPayload())));
    }

    private WorkflowResponse response(UUID runId, UUID userId) {
        WorkflowRun run = store.requireOwned(runId, userId);
        ContextPacket packet = store.packet(runId);
        ContextPacketResponse packetResponse = packet == null
                ? null
                : new ContextPacketResponse(
                        packet.getId(),
                        packet.getTokenEstimate(),
                        packet.getEstimatedCost(),
                        packet.getExpiresAt(),
                        packet.isStale(clock.instant()),
                        packet.getCreatedAt());
        List<WorkflowStepResponse> stepResponses = store.steps(runId).stream()
                .map(step -> new WorkflowStepResponse(
                        step.getId(),
                        step.getStepName(),
                        step.getStatus(),
                        step.getAttempt(),
                        step.getErrorCode(),
                        step.getErrorMessage(),
                        step.getStartedAt(),
                        step.getFinishedAt()))
                .toList();
        List<StoryFactResponse> factResponses = reviews.candidates(runId).stream()
                .map(fact -> new StoryFactResponse(
                        fact.getId(),
                        fact.getCandidateIndex(),
                        fact.getFactKey(),
                        fact.getContent(),
                        fact.getEvidence(),
                        fact.getParagraphKey(),
                        fact.getStatus(),
                        fact.getCreatedAt()))
                .toList();
        List<ReviewIssueResponse> issueResponses = reviews.issues(runId).stream()
                .map(issue -> new ReviewIssueResponse(
                        issue.getId(),
                        issue.getSource(),
                        issue.getCategory(),
                        issue.getSeverity(),
                        issue.getMessage(),
                        issue.getEvidence(),
                        issue.getHistoricalEvidence(),
                        issue.getSuggestion(),
                        issue.isBlocking(),
                        issue.isResolved(),
                        issue.getCreatedAt()))
                .toList();
        return new WorkflowResponse(
                run.getId(),
                run.getProjectId(),
                run.getChapterId(),
                run.getViewpointCharacterId(),
                run.getStatus(),
                run.getDraftContent(),
                run.getPlan(),
                run.getExtraction(),
                run.getReview(),
                run.isCancelRequested(),
                run.getRecoveryCount(),
                run.getRevisionCount(),
                run.getCommittedVersionNo(),
                run.getApprovedBy(),
                run.getApprovedAt(),
                run.getFailureCode(),
                run.getFailureMessage(),
                run.getHeartbeatAt(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getVersion(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                packetResponse,
                stepResponses,
                factResponses,
                issueResponses);
    }

    private CommitProposal proposal(ApproveWorkflowRequest request) {
        return new CommitProposal(
                List.copyOf(request.acceptedFactIndexes()),
                request.characterStateChanges().stream()
                        .map(value -> new CharacterStateChange(
                                value.characterId(),
                                value.lifeStatus(),
                                value.currentLocation(),
                                value.physicalCondition(),
                                value.emotionalState(),
                                value.abilities(),
                                value.inventoryNotes(),
                                value.notes(),
                                value.expectedVersion(),
                                value.evidence()))
                        .toList(),
                request.itemChanges().stream()
                        .map(value -> new ItemChange(
                                value.itemKey().strip(),
                                value.itemName().strip(),
                                value.fromOwnerCharacterId(),
                                value.toOwnerCharacterId(),
                                value.status(),
                                value.evidence()))
                        .toList(),
                request.timelineEvents().stream()
                        .map(value -> new TimelineEvent(
                                List.copyOf(value.participantIds()),
                                List.copyOf(value.knownByIds()),
                                value.location(),
                                value.storyTime(),
                                value.action(),
                                value.result(),
                                value.importance(),
                                value.evidence()))
                        .toList(),
                request.knowledgeChanges().stream()
                        .map(value -> new KnowledgeChange(
                                value.characterId(),
                                value.factKey().strip(),
                                value.content(),
                                value.certainty(),
                                value.sourceEventId(),
                                value.evidence()))
                        .toList());
    }

    private void validateIdempotencyKey(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new BadRequestException(
                    "invalid_idempotency_key", "Idempotency-Key must contain 8-128 safe characters");
        }
    }
}
