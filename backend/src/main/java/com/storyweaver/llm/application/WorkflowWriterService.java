package com.storyweaver.llm.application;

import com.storyweaver.llm.adapter.DeepSeekAdapter;
import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.llm.domain.DeepSeekModels.Prompt;
import com.storyweaver.llm.domain.DeepSeekModels.StreamResult;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.usage.application.UsageService;
import com.storyweaver.usage.application.UsageService.UsageInput;
import com.storyweaver.usage.domain.UsageStatus;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class WorkflowWriterService implements WorkflowWriterGateway {
    private final DeepSeekAdapter adapter;
    private final PromptCatalog prompts;
    private final ProjectAccessService projectAccess;
    private final UsageService usage;
    private final WriterConcurrencyGuard concurrency;
    private final Clock clock;

    public WorkflowWriterService(
            DeepSeekAdapter adapter,
            PromptCatalog prompts,
            ProjectAccessService projectAccess,
            UsageService usage,
            WriterConcurrencyGuard concurrency,
            Clock clock) {
        this.adapter = adapter;
        this.prompts = prompts;
        this.projectAccess = projectAccess;
        this.usage = usage;
        this.concurrency = concurrency;
        this.clock = clock;
    }

    @Override
    public WriterResult write(UUID projectId, UUID userId, AgentInput input, Consumer<String> chunks) {
        projectAccess.requireOwnedProject(projectId, userId);
        long started = clock.millis();
        try (WriterConcurrencyGuard.Lease ignored = concurrency.acquire(projectId, userId)) {
            String userPrompt = "Instruction:\n" + input.instruction() + "\n\nContext:\n" + input.context();
            StreamResult result = adapter.stream(
                    DeepSeekAgent.WRITER,
                    new Prompt(prompts.system(DeepSeekAgent.WRITER), userPrompt),
                    userId,
                    chunks::accept);
            record(projectId, userId, result, UsageStatus.SUCCEEDED);
            return new WriterResult(
                    result.requestId(),
                    result.model(),
                    result.finishReason(),
                    result.usage().promptTokens(),
                    result.usage().completionTokens(),
                    result.usage().promptCacheHitTokens(),
                    result.usage().promptCacheMissTokens(),
                    result.durationMillis());
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
            throw exception;
        }
    }

    private void record(UUID projectId, UUID userId, StreamResult result, UsageStatus status) {
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
                1,
                result.durationMillis()));
    }
}
