package com.storyweaver.memory.api;

import com.storyweaver.llm.domain.EmbeddingStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class StoryEventDtos {
    private StoryEventDtos() {}

    public record CreateEventRequest(
            UUID chapterId,
            @NotNull @Size(max = 100) List<UUID> participantIds,
            @NotNull @Size(max = 100) List<UUID> knownByIds,
            @Size(max = 200) String location,
            @Size(max = 200) String storyTime,
            @NotBlank @Size(max = 200000) String action,
            @NotBlank @Size(max = 200000) String result,
            @DecimalMin("0.0") @DecimalMax("1.0") double importance,
            @Size(max = 200) String evidenceParagraph) {}

    public record UpdateEventRequest(
            UUID chapterId,
            @NotNull @Size(max = 100) List<UUID> participantIds,
            @NotNull @Size(max = 100) List<UUID> knownByIds,
            @Size(max = 200) String location,
            @Size(max = 200) String storyTime,
            @NotBlank @Size(max = 200000) String action,
            @NotBlank @Size(max = 200000) String result,
            @DecimalMin("0.0") @DecimalMax("1.0") double importance,
            @Size(max = 200) String evidenceParagraph,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record SearchEventsRequest(
            @NotBlank @Size(max = 200000) String query,
            @NotNull @Size(max = 100) List<UUID> participantIds,
            @Size(max = 200) String location,
            @Min(1) Integer chapterNo,
            @Min(1) @Max(100) Integer topK) {}

    public record EventResponse(
            UUID id,
            UUID projectId,
            UUID chapterId,
            Integer chapterNo,
            List<UUID> participantIds,
            List<UUID> knownByIds,
            String location,
            String storyTime,
            String action,
            String result,
            double importance,
            String evidenceParagraph,
            EmbeddingStatus embeddingStatus,
            String embeddingModel,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record EventMatchResponse(
            EventResponse event,
            double score,
            double semanticSimilarity,
            double participantScore,
            double locationScore,
            double chapterProximityScore,
            Set<String> reasons) {}

    public record EventSearchResponse(
            boolean embeddingAvailable, String degradedReason, String query, List<EventMatchResponse> matches) {}
}
