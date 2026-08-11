package com.storyweaver.llm.application;

import com.storyweaver.llm.adapter.DeepSeekAdapter;
import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.application.AgentContracts.ChapterPlan;
import com.storyweaver.llm.application.AgentContracts.ExtractionResult;
import com.storyweaver.llm.application.AgentContracts.ReviewResult;
import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.llm.domain.DeepSeekModels.ParsedResponse;
import com.storyweaver.llm.domain.DeepSeekModels.Prompt;
import com.storyweaver.llm.domain.DeepSeekModels.Response;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.usage.application.UsageService;
import com.storyweaver.usage.application.UsageService.UsageInput;
import com.storyweaver.usage.domain.UsageStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekAgentService implements PlannerGateway, ExtractorGateway, ReviewerGateway {
    private final DeepSeekAdapter adapter;
    private final PromptCatalog prompts;
    private final ProjectAccessService projectAccess;
    private final UsageService usage;
    private final Clock clock;

    public DeepSeekAgentService(
            DeepSeekAdapter adapter,
            PromptCatalog prompts,
            ProjectAccessService projectAccess,
            UsageService usage,
            Clock clock) {
        this.adapter = adapter;
        this.prompts = prompts;
        this.projectAccess = projectAccess;
        this.usage = usage;
        this.clock = clock;
    }

    @Override
    public ChapterPlan plan(UUID projectId, UUID userId, AgentInput request) {
        return invoke(projectId, userId, DeepSeekAgent.PLANNER, request, ChapterPlan.class)
                .value();
    }

    @Override
    public ExtractionResult extract(UUID projectId, UUID userId, AgentInput request) {
        return invoke(projectId, userId, DeepSeekAgent.EXTRACTOR, request, ExtractionResult.class)
                .value();
    }

    @Override
    public ReviewResult review(UUID projectId, UUID userId, AgentInput request) {
        return invoke(projectId, userId, DeepSeekAgent.REVIEWER, request, ReviewResult.class)
                .value();
    }

    private <T> ParsedResponse<T> invoke(
            UUID projectId, UUID userId, DeepSeekAgent agent, AgentInput input, Class<T> type) {
        projectAccess.requireOwnedProject(projectId, userId);
        long started = clock.millis();
        try {
            String userPrompt = "Instruction:\n" + input.instruction() + "\n\nContext:\n" + input.context();
            ParsedResponse<T> result =
                    adapter.completeJson(agent, new Prompt(prompts.system(agent), userPrompt), userId, type);
            record(projectId, userId, agent, result.response(), UsageStatus.SUCCEEDED);
            return result;
        } catch (RuntimeException exception) {
            usage.record(new UsageInput(
                    projectId,
                    userId,
                    agent.name(),
                    agent.model(),
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

    private void record(UUID projectId, UUID userId, DeepSeekAgent agent, Response response, UsageStatus status) {
        var tokens = response.usage();
        usage.record(new UsageInput(
                projectId,
                userId,
                agent.name(),
                response.model(),
                response.requestId(),
                status,
                tokens.promptTokens(),
                tokens.completionTokens(),
                tokens.reasoningTokens(),
                tokens.promptCacheHitTokens(),
                tokens.promptCacheMissTokens(),
                response.attempts(),
                response.durationMillis()));
    }
}
