package com.storyweaver.workflow.application;

import com.storyweaver.consistency.application.ConsistencyReviewService;
import com.storyweaver.consistency.application.ConsistencyValidatorEngine;
import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.application.ExtractorGateway;
import com.storyweaver.llm.application.PlannerGateway;
import com.storyweaver.llm.application.ReviewerGateway;
import com.storyweaver.llm.application.WorkflowWriterGateway;
import com.storyweaver.llm.application.WorkflowWriterGateway.WriterResult;
import com.storyweaver.workflow.domain.ContextPacket;
import com.storyweaver.workflow.domain.WorkflowRun;
import com.storyweaver.workflow.domain.WorkflowStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class WorkflowOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrator.class);
    private final WorkflowStore store;
    private final WorkflowPreflight preflight;
    private final WorkflowContextBuilder contexts;
    private final PlannerGateway planner;
    private final WorkflowWriterGateway writer;
    private final ExtractorGateway extractor;
    private final ReviewerGateway reviewer;
    private final ConsistencyValidatorEngine validators;
    private final ConsistencyReviewService reviews;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final ObservationRegistry observations;
    private final Clock clock;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final Set<UUID> resubmitRequested = ConcurrentHashMap.newKeySet();

    public WorkflowOrchestrator(
            WorkflowStore store,
            WorkflowPreflight preflight,
            WorkflowContextBuilder contexts,
            PlannerGateway planner,
            WorkflowWriterGateway writer,
            ExtractorGateway extractor,
            ReviewerGateway reviewer,
            ConsistencyValidatorEngine validators,
            ConsistencyReviewService reviews,
            ExecutorService aiTaskExecutor,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            ObservationRegistry observations,
            Clock clock) {
        this.store = store;
        this.preflight = preflight;
        this.contexts = contexts;
        this.planner = planner;
        this.writer = writer;
        this.extractor = extractor;
        this.reviewer = reviewer;
        this.validators = validators;
        this.reviews = reviews;
        this.executor = aiTaskExecutor;
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.observations = observations;
        this.clock = clock;
    }

    public void submit(UUID runId) {
        if (!running.add(runId)) {
            resubmitRequested.add(runId);
            return;
        }
        executor.submit(() -> execute(runId));
    }

    private void execute(UUID runId) {
        long started = clock.millis();
        String currentStep = "WORKFLOW";
        Observation trace = Observation.createNotStarted("storyweaver.workflow", observations)
                .lowCardinalityKeyValue("step", "WORKFLOW")
                .start();
        Observation.Scope traceScope = trace.openScope();
        try {
            while (true) {
                WorkflowRun run = store.require(runId);
                if (!run.getStatus().isExecuting()) return;
                currentStep = stepFor(run.getStatus());
                String observedStep = currentStep;
                switch (run.getStatus()) {
                    case CREATED, PREFLIGHT -> {
                        observe(observedStep, () -> preflightAndContext(run));
                    }
                    case CONTEXT_READY, PLANNING -> {
                        observe(observedStep, () -> plan(run));
                    }
                    case PLAN_READY, WRITING -> {
                        observe(observedStep, () -> write(run));
                    }
                    case TEXT_READY, EXTRACTING -> {
                        observe(observedStep, () -> extract(run));
                    }
                    case VALIDATING -> {
                        observe(observedStep, () -> validate(run));
                    }
                    case REVIEWING -> {
                        observe(observedStep, () -> review(run));
                    }
                    default -> {}
                }
            }
        } catch (WorkflowCancelledException ignored) {
            // The cancellation endpoint already persisted the terminal state and event.
        } catch (WorkflowBlockedException exception) {
            trace.error(exception);
            meters.counter("storyweaver.workflow.failures", "step", currentStep, "status", "BLOCKED")
                    .increment();
            store.block(runId, currentStep, exception);
        } catch (RuntimeException exception) {
            trace.error(exception);
            meters.counter("storyweaver.workflow.failures", "step", currentStep, "status", "FAILED")
                    .increment();
            log.error("Workflow {} failed during {}", runId, currentStep, exception);
            store.fail(runId, currentStep, exception);
        } finally {
            running.remove(runId);
            WorkflowStatus outcome = store.require(runId).getStatus();
            meters.counter("storyweaver.workflow.runs", "status", outcome.name())
                    .increment();
            meters.timer("storyweaver.workflow.duration", "status", outcome.name())
                    .record(Duration.ofMillis(clock.millis() - started));
            trace.lowCardinalityKeyValue("status", outcome.name());
            traceScope.close();
            trace.stop();
            if (resubmitRequested.remove(runId) && outcome.isExecuting()) submit(runId);
        }
    }

    private void observe(String step, Runnable action) {
        Observation.createNotStarted("storyweaver.workflow.step", observations)
                .lowCardinalityKeyValue("step", step)
                .observe(action);
    }

    private void preflightAndContext(WorkflowRun run) {
        if (run.getStatus() == WorkflowStatus.CREATED) store.transition(run.getId(), WorkflowStatus.PREFLIGHT);
        store.startStep(run.getId(), "PREFLIGHT");
        preflight.check(store.require(run.getId()));
        store.completeStep(run.getId(), "PREFLIGHT");
        ContextPacket existing = store.packet(run.getId());
        if (existing == null) {
            store.startStep(run.getId(), "CONTEXT");
            ContextPacket packet = Observation.createNotStarted("storyweaver.workflow.context", observations)
                    .lowCardinalityKeyValue("step", "CONTEXT_BUILD")
                    .observe(() -> contexts.build(store.require(run.getId())));
            store.savePacket(packet);
            store.completeStep(run.getId(), "CONTEXT");
        }
        store.transition(run.getId(), WorkflowStatus.CONTEXT_READY);
    }

    private void plan(WorkflowRun run) {
        if (run.getStatus() == WorkflowStatus.CONTEXT_READY) {
            store.transition(run.getId(), WorkflowStatus.PLANNING);
        }
        store.startStep(run.getId(), "PLANNING");
        WorkflowRun current = store.require(run.getId());
        ContextPacket packet = freshPacket(current);
        var result = planner.plan(
                current.getProjectId(),
                current.getUserId(),
                new AgentInput(
                        "Create an executable chapter plan for this workflow. " + current.getInstruction(),
                        render(packet)));
        store.savePlan(run.getId(), map(result));
        store.completeStep(run.getId(), "PLANNING");
        store.transition(run.getId(), WorkflowStatus.PLAN_READY);
    }

    private void write(WorkflowRun run) {
        if (run.getStatus() == WorkflowStatus.PLAN_READY) {
            store.transition(run.getId(), WorkflowStatus.WRITING);
        }
        store.startStep(run.getId(), "WRITING");
        WorkflowRun current = store.require(run.getId());
        ContextPacket packet = freshPacket(current);
        String context = render(packet) + "\n\nPLAN:\n" + objectMapper.writeValueAsString(current.getPlan());
        WriterResult result = writer.write(
                current.getProjectId(),
                current.getUserId(),
                new AgentInput("Write the runtime chapter draft. " + current.getInstruction(), context),
                chunk -> store.appendDraft(run.getId(), chunk));
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("model", result.model());
        usage.put("finishReason", result.finishReason());
        usage.put("promptTokens", result.promptTokens());
        usage.put("completionTokens", result.completionTokens());
        usage.put("cacheHitTokens", result.cacheHitTokens());
        usage.put("cacheMissTokens", result.cacheMissTokens());
        store.writerCompleted(run.getId(), usage);
        store.completeStep(run.getId(), "WRITING");
        store.transition(run.getId(), WorkflowStatus.TEXT_READY);
    }

    private void extract(WorkflowRun run) {
        if (run.getStatus() == WorkflowStatus.TEXT_READY) {
            store.transition(run.getId(), WorkflowStatus.EXTRACTING);
        }
        store.startStep(run.getId(), "EXTRACTING");
        WorkflowRun current = store.require(run.getId());
        freshPacket(current);
        var result = extractor.extract(
                current.getProjectId(),
                current.getUserId(),
                new AgentInput(
                        "Extract structured runtime facts with evidence. Do not commit them.",
                        current.getDraftContent()));
        store.saveExtraction(run.getId(), map(result));
        WorkflowRun extracted = store.require(run.getId());
        reviews.replaceCandidates(
                extracted.getProjectId(),
                extracted.getId(),
                extracted.getChapterId(),
                extracted.getExtraction(),
                extracted.getDraftContent());
        store.completeStep(run.getId(), "EXTRACTING");
        store.transition(run.getId(), WorkflowStatus.VALIDATING);
    }

    private void validate(WorkflowRun run) {
        store.startStep(run.getId(), "VALIDATING");
        WorkflowRun current = store.require(run.getId());
        freshPacket(current);
        var issues = validators.validateDraft(
                current.getProjectId(), current.getViewpointCharacterId(), current.getDraftContent());
        reviews.replaceJavaIssues(current.getProjectId(), current.getId(), issues);
        store.completeStep(run.getId(), "VALIDATING");
        store.transition(run.getId(), WorkflowStatus.REVIEWING);
    }

    private void review(WorkflowRun run) {
        store.startStep(run.getId(), "REVIEWING");
        WorkflowRun current = store.require(run.getId());
        ContextPacket packet = freshPacket(current);
        Map<String, Object> reviewContext = new LinkedHashMap<>();
        reviewContext.put("draft", current.getDraftContent());
        reviewContext.put("extraction", current.getExtraction());
        reviewContext.put("javaIssues", reviews.issues(current.getId()));
        reviewContext.put("contextPacket", render(packet));
        var result = reviewer.review(
                current.getProjectId(),
                current.getUserId(),
                new AgentInput(
                        "Review the runtime draft and deterministic issues. Do not commit any state.",
                        objectMapper.writeValueAsString(reviewContext)));
        store.saveReview(run.getId(), map(result));
        reviews.appendReviewerIssues(current.getProjectId(), current.getId(), result);
        store.completeStep(run.getId(), "REVIEWING");
        store.transition(run.getId(), WorkflowStatus.WAITING_APPROVAL);
    }

    private ContextPacket freshPacket(WorkflowRun run) {
        ContextPacket packet = store.packet(run.getId());
        if (packet == null) throw new WorkflowBlockedException("context_packet_missing", "Context Packet is missing");
        if (packet.isStale(clock.instant())) {
            throw new WorkflowBlockedException("context_packet_stale", "Context Packet expired before this step");
        }
        return packet;
    }

    private String render(ContextPacket packet) {
        return objectMapper.writeValueAsString(Map.of(
                "context", packet.getContextData(),
                "worldbook", packet.getWorldbookReport(),
                "memory", packet.getMemoryReport(),
                "skills", packet.getSkillSnapshot()));
    }

    private String stepFor(WorkflowStatus status) {
        return switch (status) {
            case CREATED, PREFLIGHT -> "PREFLIGHT";
            case CONTEXT_READY, PLANNING -> "PLANNING";
            case PLAN_READY, WRITING -> "WRITING";
            case TEXT_READY, EXTRACTING -> "EXTRACTING";
            case VALIDATING -> "VALIDATING";
            case REVIEWING -> "REVIEWING";
            default -> "WORKFLOW";
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }
}
