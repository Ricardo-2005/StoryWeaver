package com.storyweaver.llm.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AgentContracts {
    private AgentContracts() {}

    public record ChapterPlan(
            @NotBlank String chapterTitle,
            @NotBlank String chapterGoal,
            String viewpointCharacterId,
            @NotEmpty List<@Valid ScenePlan> scenes,
            @NotNull List<String> mustInclude,
            @NotNull List<String> mustAvoid,
            @NotBlank String exitHook) {}

    public record ScenePlan(
            @NotBlank String title,
            @NotBlank String goal,
            @NotBlank String summary,
            @NotNull List<String> mustInclude,
            @NotNull List<String> mustAvoid) {}

    public record ExtractionResult(
            @NotBlank String summary,
            @NotNull List<String> events,
            @NotNull List<String> candidateFacts,
            @NotNull List<String> characterChanges,
            @NotNull List<String> itemTransfers,
            @NotNull List<String> knowledgeTransfers) {}

    public record ReviewResult(@NotNull List<@Valid ReviewIssue> issues, @NotBlank String overallAssessment) {}

    public record ReviewIssue(
            @NotBlank String category,
            @NotNull ReviewSeverity severity,
            @NotBlank String message,
            @NotBlank String evidence,
            @NotBlank String suggestion,
            boolean blocking) {}

    public enum ReviewSeverity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        BLOCKER
    }

    public record AgentInput(
            @NotBlank @Size(max = 20000) String instruction, @NotBlank @Size(max = 400000) String context) {}
}
