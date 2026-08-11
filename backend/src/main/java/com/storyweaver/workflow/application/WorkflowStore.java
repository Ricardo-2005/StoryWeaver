package com.storyweaver.workflow.application;

import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ApiException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.workflow.domain.ContextPacket;
import com.storyweaver.workflow.domain.WorkflowEvent;
import com.storyweaver.workflow.domain.WorkflowRun;
import com.storyweaver.workflow.domain.WorkflowStateMachine;
import com.storyweaver.workflow.domain.WorkflowStatus;
import com.storyweaver.workflow.domain.WorkflowStep;
import com.storyweaver.workflow.repository.ContextPacketRepository;
import com.storyweaver.workflow.repository.WorkflowEventRepository;
import com.storyweaver.workflow.repository.WorkflowRunRepository;
import com.storyweaver.workflow.repository.WorkflowStepRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowStore {
    private static final EnumSet<WorkflowStatus> EXECUTING = EnumSet.of(
            WorkflowStatus.CREATED,
            WorkflowStatus.PREFLIGHT,
            WorkflowStatus.CONTEXT_READY,
            WorkflowStatus.PLANNING,
            WorkflowStatus.PLAN_READY,
            WorkflowStatus.WRITING,
            WorkflowStatus.TEXT_READY,
            WorkflowStatus.EXTRACTING,
            WorkflowStatus.VALIDATING,
            WorkflowStatus.REVIEWING);
    private static final EnumSet<WorkflowStatus> ACTIVE_PROJECT = EnumSet.copyOf(EXECUTING);

    static {
        ACTIVE_PROJECT.add(WorkflowStatus.COMMITTING);
    }

    private final WorkflowRunRepository runs;
    private final ContextPacketRepository packets;
    private final WorkflowStepRepository steps;
    private final WorkflowEventRepository events;
    private final ProjectAccessService projectAccess;
    private final WorkflowStateMachine stateMachine;
    private final Clock clock;

    public WorkflowStore(
            WorkflowRunRepository runs,
            ContextPacketRepository packets,
            WorkflowStepRepository steps,
            WorkflowEventRepository events,
            ProjectAccessService projectAccess,
            WorkflowStateMachine stateMachine,
            Clock clock) {
        this.runs = runs;
        this.packets = packets;
        this.steps = steps;
        this.events = events;
        this.projectAccess = projectAccess;
        this.stateMachine = stateMachine;
        this.clock = clock;
    }

    @Transactional
    public StartResult create(
            UUID projectId,
            UUID chapterId,
            UUID userId,
            UUID viewpointCharacterId,
            String idempotencyKey,
            String instruction) {
        WorkflowRun existing =
                runs.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.getChapterId().equals(chapterId)
                    || !existing.getViewpointCharacterId().equals(viewpointCharacterId)
                    || !existing.getInstruction().equals(instruction)) {
                throw new ConflictException(
                        "idempotency_key_reused", "Idempotency-Key was already used for another workflow request");
            }
            return new StartResult(existing, false);
        }
        if (runs.existsByProjectIdAndStatusIn(projectId, ACTIVE_PROJECT)) {
            throw new ConflictException("workflow_project_busy", "This project already has an active workflow");
        }
        Instant now = clock.instant();
        WorkflowRun run =
                new WorkflowRun(projectId, chapterId, userId, viewpointCharacterId, idempotencyKey, instruction, now);
        try {
            runs.saveAndFlush(run);
        } catch (DataIntegrityViolationException exception) {
            WorkflowRun raced =
                    runs.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
            if (raced != null) return new StartResult(raced, false);
            throw new ConflictException("workflow_project_busy", "This project already has an active workflow");
        }
        event(run, "workflow.created", WorkflowStatus.CREATED.name(), Map.of());
        return new StartResult(run, true);
    }

    @Transactional(readOnly = true)
    public WorkflowRun requireOwned(UUID runId, UUID userId) {
        WorkflowRun run = require(runId);
        projectAccess.requireOwnedProject(run.getProjectId(), userId);
        return run;
    }

    @Transactional(readOnly = true)
    public WorkflowRun require(UUID runId) {
        return runs.findById(runId)
                .orElseThrow(() -> new NotFoundException("workflow_not_found", "Workflow was not found"));
    }

    @Transactional(readOnly = true)
    public ContextPacket packet(UUID runId) {
        return packets.findByWorkflowRunId(runId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<WorkflowStep> steps(UUID runId) {
        return steps.findAllByWorkflowRunIdOrderByStartedAtAsc(runId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowEvent> eventsAfter(UUID runId, long afterEventId) {
        return events.findTop200ByWorkflowRunIdAndEventIdGreaterThanOrderByEventIdAsc(runId, afterEventId);
    }

    @Transactional
    public WorkflowRun transition(UUID runId, WorkflowStatus next) {
        WorkflowRun run = require(runId);
        checkCancellation(run);
        run.transition(next, stateMachine, clock.instant());
        event(run, "workflow.step", next.name(), Map.of("status", next.name()));
        return run;
    }

    @Transactional
    public void startStep(UUID runId, String stepName) {
        WorkflowRun run = require(runId);
        checkCancellation(run);
        Instant now = clock.instant();
        WorkflowStep step =
                steps.findByWorkflowRunIdAndStepName(runId, stepName).orElse(null);
        if (step == null) {
            step = new WorkflowStep(run.getProjectId(), runId, stepName, now);
        } else {
            step.restart(now);
        }
        steps.save(step);
        run.heartbeat(now);
        event(run, "workflow.step", stepName, Map.of("stepStatus", "RUNNING", "attempt", step.getAttempt()));
    }

    @Transactional
    public void completeStep(UUID runId, String stepName) {
        WorkflowRun run = require(runId);
        WorkflowStep step = requireStep(runId, stepName);
        step.complete(clock.instant());
        event(run, "workflow.step", stepName, Map.of("stepStatus", "COMPLETED", "attempt", step.getAttempt()));
    }

    @Transactional
    public void savePacket(ContextPacket packet) {
        packets.save(packet);
        require(packet.getWorkflowRunId()).heartbeat(clock.instant());
    }

    @Transactional
    public void savePlan(UUID runId, Map<String, Object> plan) {
        require(runId).plan(plan, clock.instant());
    }

    @Transactional
    public void appendDraft(UUID runId, String text) {
        WorkflowRun run = require(runId);
        checkCancellation(run);
        run.appendDraft(text, clock.instant());
        event(run, "text.delta", WorkflowStatus.WRITING.name(), Map.of("text", text));
    }

    @Transactional
    public void writerCompleted(UUID runId, Map<String, Object> usage) {
        WorkflowRun run = require(runId);
        event(run, "usage.partial", WorkflowStatus.WRITING.name(), usage);
        event(
                run,
                "text.completed",
                WorkflowStatus.WRITING.name(),
                Map.of("draftLength", run.getDraftContent().length()));
    }

    @Transactional
    public void saveExtraction(UUID runId, Map<String, Object> extraction) {
        require(runId).extraction(extraction, clock.instant());
    }

    @Transactional
    public void saveReview(UUID runId, Map<String, Object> review) {
        require(runId).review(review, clock.instant());
    }

    @Transactional
    public WorkflowRun revise(UUID runId, UUID userId, String revisedDraft) {
        WorkflowRun run = requireOwned(runId, userId);
        if (run.getStatus() != WorkflowStatus.WAITING_APPROVAL && run.getStatus() != WorkflowStatus.REVISION_REQUIRED) {
            throw new ConflictException("workflow_not_revisable", "Workflow is not waiting for revision");
        }
        if (run.getStatus() == WorkflowStatus.WAITING_APPROVAL) {
            run.transition(WorkflowStatus.REVISION_REQUIRED, stateMachine, clock.instant());
            event(run, "workflow.step", WorkflowStatus.REVISION_REQUIRED.name(), Map.of("status", "REVISION_REQUIRED"));
        }
        run.revisedDraft(revisedDraft, clock.instant());
        run.transition(WorkflowStatus.TEXT_READY, stateMachine, clock.instant());
        event(
                run,
                "text.completed",
                WorkflowStatus.TEXT_READY.name(),
                Map.of("draftLength", revisedDraft.length(), "revisionCount", run.getRevisionCount()));
        return run;
    }

    @Transactional
    public WorkflowRun cancel(UUID runId, UUID userId) {
        WorkflowRun run = runs.findWithLockById(runId)
                .orElseThrow(() -> new NotFoundException("workflow_not_found", "Workflow was not found"));
        projectAccess.requireOwnedProject(run.getProjectId(), userId);
        if (run.getStatus() == WorkflowStatus.CANCELLED) return run;
        if (EnumSet.of(
                        WorkflowStatus.BLOCKED,
                        WorkflowStatus.FAILED,
                        WorkflowStatus.COMPLETED,
                        WorkflowStatus.ROLLED_BACK)
                .contains(run.getStatus())) {
            throw new ConflictException("workflow_not_cancellable", "Workflow is already finished");
        }
        run.requestCancellation(clock.instant());
        run.transition(WorkflowStatus.CANCELLED, stateMachine, clock.instant());
        event(run, "workflow.cancelled", WorkflowStatus.CANCELLED.name(), Map.of());
        return run;
    }

    @Transactional
    public void fail(UUID runId, String stepName, RuntimeException exception) {
        WorkflowRun run = require(runId);
        if (!run.getStatus().isExecuting()) return;
        String code = exception instanceof ApiException api ? api.getCode() : "workflow_failed";
        String message = safeMessage(exception);
        failStep(runId, stepName, code, message);
        run.failed(code, message, clock.instant());
        run.transition(WorkflowStatus.FAILED, stateMachine, clock.instant());
        event(run, "workflow.error", WorkflowStatus.FAILED.name(), Map.of("code", code, "message", message));
    }

    @Transactional
    public void block(UUID runId, String stepName, WorkflowBlockedException exception) {
        WorkflowRun run = require(runId);
        if (!run.getStatus().isExecuting()) return;
        failStep(runId, stepName, exception.code(), exception.getMessage());
        run.failed(exception.code(), exception.getMessage(), clock.instant());
        run.transition(WorkflowStatus.BLOCKED, stateMachine, clock.instant());
        event(
                run,
                "workflow.error",
                WorkflowStatus.BLOCKED.name(),
                Map.of("code", exception.code(), "message", exception.getMessage()));
    }

    @Transactional
    public void prepareRecovery(UUID runId) {
        WorkflowRun run = require(runId);
        if (!run.getStatus().isExecuting()) return;
        if (run.getStatus() == WorkflowStatus.WRITING) run.resetDraftForRecovery(clock.instant());
        else run.markRecovered(clock.instant());
        event(
                run,
                "warning",
                run.getStatus().name(),
                Map.of("code", "workflow_recovered", "recoveryCount", run.getRecoveryCount()));
    }

    @Transactional
    public void rolledBack(UUID runId, String message) {
        WorkflowRun run = require(runId);
        if (run.getStatus() != WorkflowStatus.WAITING_APPROVAL && run.getStatus() != WorkflowStatus.COMMITTING) return;
        run.failed("atomic_commit_rolled_back", message, clock.instant());
        run.transition(WorkflowStatus.ROLLED_BACK, stateMachine, clock.instant());
        event(
                run,
                "workflow.error",
                WorkflowStatus.ROLLED_BACK.name(),
                Map.of("code", "atomic_commit_rolled_back", "message", message));
    }

    @Transactional(readOnly = true)
    public List<WorkflowRun> staleRuns(Instant heartbeatBefore) {
        return runs.findAllByStatusInAndHeartbeatAtBefore(EXECUTING, heartbeatBefore);
    }

    private WorkflowStep requireStep(UUID runId, String stepName) {
        return steps.findByWorkflowRunIdAndStepName(runId, stepName)
                .orElseThrow(() -> new IllegalStateException("Workflow step is missing: " + stepName));
    }

    private void failStep(UUID runId, String stepName, String code, String message) {
        steps.findByWorkflowRunIdAndStepName(runId, stepName)
                .ifPresent(step -> step.fail(code, message, clock.instant()));
    }

    private void checkCancellation(WorkflowRun run) {
        if (run.isCancelRequested() || run.getStatus() == WorkflowStatus.CANCELLED) {
            throw new WorkflowCancelledException();
        }
    }

    private WorkflowEvent event(WorkflowRun run, String eventType, String stepName, Map<String, Object> payload) {
        return events.save(
                new WorkflowEvent(run.getProjectId(), run.getId(), eventType, stepName, payload, clock.instant()));
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public record StartResult(WorkflowRun run, boolean created) {}
}
