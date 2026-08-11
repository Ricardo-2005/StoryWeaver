package com.storyweaver.llm.application;

import com.storyweaver.llm.adapter.DeepSeekAdapter;
import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.llm.domain.DeepSeekModels.Prompt;
import com.storyweaver.llm.domain.DeepSeekModels.StreamResult;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ApiException;
import com.storyweaver.usage.application.UsageService;
import com.storyweaver.usage.application.UsageService.UsageInput;
import com.storyweaver.usage.domain.UsageStatus;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class WriterStreamingService implements WriterGateway {
    private static final long EMITTER_TIMEOUT_MILLIS = 330_000;

    private final DeepSeekAdapter adapter;
    private final PromptCatalog prompts;
    private final ProjectAccessService projectAccess;
    private final UsageService usage;
    private final WriterConcurrencyGuard concurrency;
    private final ExecutorService aiTaskExecutor;
    private final ScheduledExecutorService aiHeartbeatExecutor;
    private final Clock clock;

    public WriterStreamingService(
            DeepSeekAdapter adapter,
            PromptCatalog prompts,
            ProjectAccessService projectAccess,
            UsageService usage,
            WriterConcurrencyGuard concurrency,
            ExecutorService aiTaskExecutor,
            ScheduledExecutorService aiHeartbeatExecutor,
            Clock clock) {
        this.adapter = adapter;
        this.prompts = prompts;
        this.projectAccess = projectAccess;
        this.usage = usage;
        this.concurrency = concurrency;
        this.aiTaskExecutor = aiTaskExecutor;
        this.aiHeartbeatExecutor = aiHeartbeatExecutor;
        this.clock = clock;
    }

    @Override
    public SseEmitter stream(UUID projectId, UUID userId, AgentInput input) {
        projectAccess.requireOwnedProject(projectId, userId);
        WriterConcurrencyGuard.Lease lease = concurrency.acquire(projectId, userId);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        EventPublisher events = new EventPublisher(emitter, UUID.randomUUID());
        AtomicBoolean finished = new AtomicBoolean();
        ScheduledFuture<?> heartbeat = aiHeartbeatExecutor.scheduleAtFixedRate(
                () -> events.send("heartbeat", Map.of()), 15, 15, TimeUnit.SECONDS);
        Runnable cleanup = () -> {
            if (finished.compareAndSet(false, true)) {
                heartbeat.cancel(false);
                lease.close();
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        aiTaskExecutor.submit(() -> run(projectId, userId, input, events, emitter, cleanup));
        return emitter;
    }

    private void run(
            UUID projectId,
            UUID userId,
            AgentInput input,
            EventPublisher events,
            SseEmitter emitter,
            Runnable cleanup) {
        long started = clock.millis();
        try {
            String userPrompt = "Instruction:\n" + input.instruction() + "\n\nContext:\n" + input.context();
            StreamResult result = adapter.stream(
                    DeepSeekAgent.WRITER,
                    new Prompt(prompts.system(DeepSeekAgent.WRITER), userPrompt),
                    userId,
                    delta -> events.send("text.delta", Map.of("text", delta)));
            record(projectId, userId, result, UsageStatus.SUCCEEDED, 1);
            events.send(
                    "usage.partial",
                    Map.of(
                            "promptTokens", result.usage().promptTokens(),
                            "completionTokens", result.usage().completionTokens(),
                            "cacheHitTokens", result.usage().promptCacheHitTokens(),
                            "cacheMissTokens", result.usage().promptCacheMissTokens()));
            events.send(
                    "text.completed",
                    Map.of(
                            "requestId", result.requestId() == null ? "" : result.requestId(),
                            "finishReason", result.finishReason() == null ? "" : result.finishReason()));
            emitter.complete();
        } catch (RuntimeException exception) {
            usage.record(new UsageInput(
                    projectId,
                    userId,
                    DeepSeekAgent.WRITER.name(),
                    DeepSeekAgent.WRITER.model(),
                    null,
                    UsageStatus.FAILED,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1,
                    clock.millis() - started));
            String code = exception instanceof ApiException api ? api.getCode() : "writer_failed";
            events.send("workflow.error", Map.of("code", code, "message", safeMessage(exception)));
            emitter.completeWithError(exception);
        } finally {
            cleanup.run();
        }
    }

    private void record(UUID projectId, UUID userId, StreamResult result, UsageStatus status, int attempts) {
        var tokens = result.usage();
        usage.record(new UsageInput(
                projectId,
                userId,
                DeepSeekAgent.WRITER.name(),
                result.model(),
                result.requestId(),
                status,
                tokens.promptTokens(),
                tokens.completionTokens(),
                tokens.reasoningTokens(),
                tokens.promptCacheHitTokens(),
                tokens.promptCacheMissTokens(),
                attempts,
                result.durationMillis()));
    }

    private String safeMessage(RuntimeException exception) {
        return exception instanceof ApiException ? exception.getMessage() : "Writer streaming failed";
    }

    private static final class EventPublisher {
        private final SseEmitter emitter;
        private final UUID runId;
        private final AtomicLong sequence = new AtomicLong();

        private EventPublisher(SseEmitter emitter, UUID runId) {
            this.emitter = emitter;
            this.runId = runId;
        }

        private synchronized void send(String type, Map<String, ?> payload) {
            long next = sequence.incrementAndGet();
            try {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(next))
                        .name(type)
                        .data(new WriterEvent(next, runId, type, "WRITING", Instant.now(), payload)));
            } catch (IOException | IllegalStateException ignored) {
                // Client disconnect is handled through the emitter callbacks.
            }
        }
    }

    private record WriterEvent(
            long eventId, UUID runId, String type, String step, Instant timestamp, Map<String, ?> payload) {}
}
