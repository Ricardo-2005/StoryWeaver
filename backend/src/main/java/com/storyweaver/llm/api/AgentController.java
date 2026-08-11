package com.storyweaver.llm.api;

import com.storyweaver.llm.adapter.DeepSeekRequestFactory;
import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.application.AgentContracts.ChapterPlan;
import com.storyweaver.llm.application.AgentContracts.ExtractionResult;
import com.storyweaver.llm.application.AgentContracts.ReviewResult;
import com.storyweaver.llm.application.ExtractorGateway;
import com.storyweaver.llm.application.PlannerGateway;
import com.storyweaver.llm.application.ReviewerGateway;
import com.storyweaver.llm.application.WriterGateway;
import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.llm.domain.DeepSeekModels.ConfigurationPreview;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class AgentController {
    private final PlannerGateway planner;
    private final WriterGateway writer;
    private final ExtractorGateway extractor;
    private final ReviewerGateway reviewer;
    private final DeepSeekRequestFactory requestFactory;

    public AgentController(
            PlannerGateway planner,
            WriterGateway writer,
            ExtractorGateway extractor,
            ReviewerGateway reviewer,
            DeepSeekRequestFactory requestFactory) {
        this.planner = planner;
        this.writer = writer;
        this.extractor = extractor;
        this.reviewer = reviewer;
        this.requestFactory = requestFactory;
    }

    @PostMapping("/projects/{projectId}/ai/planner")
    ChapterPlan plan(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody AgentInput request) {
        return planner.plan(projectId, userId(jwt), request);
    }

    @PostMapping(value = "/projects/{projectId}/ai/writer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter write(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody AgentInput request) {
        return writer.stream(projectId, userId(jwt), request);
    }

    @PostMapping("/projects/{projectId}/ai/extractor")
    ExtractionResult extract(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody AgentInput request) {
        return extractor.extract(projectId, userId(jwt), request);
    }

    @PostMapping("/projects/{projectId}/ai/reviewer")
    ReviewResult review(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody AgentInput request) {
        return reviewer.review(projectId, userId(jwt), request);
    }

    @GetMapping("/ai/model-config")
    List<ConfigurationPreview> modelConfig() {
        return Arrays.stream(DeepSeekAgent.values())
                .map(requestFactory::preview)
                .toList();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
