package com.storyweaver.worldbook.api;

import com.storyweaver.llm.domain.EmbeddingStatus;
import com.storyweaver.worldbook.domain.WorldbookScope;
import com.storyweaver.worldbook.domain.WorldbookVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorldbookDtos {
    private WorldbookDtos() {}

    public record CreateEntryRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 200000) String content,
            boolean active,
            boolean constantEnabled,
            boolean vectorEnabled,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 100) String> keywords,
            @Min(0) @Max(1000) int priority,
            @NotNull WorldbookScope scopeType,
            UUID scopeRefId,
            @NotNull WorldbookVisibility visibilityType,
            UUID visibilityRefId) {}

    public record UpdateEntryRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 200000) String content,
            boolean active,
            boolean constantEnabled,
            boolean vectorEnabled,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 100) String> keywords,
            @Min(0) @Max(1000) int priority,
            @NotNull WorldbookScope scopeType,
            UUID scopeRefId,
            @NotNull WorldbookVisibility visibilityType,
            UUID visibilityRefId,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record PreviewRequest(
            @NotBlank @Size(max = 200000) String query,
            UUID chapterId,
            UUID viewpointCharacterId,
            @Min(1) @Max(200000) Integer tokenBudget,
            @Min(1) @Max(100) Integer topK) {}

    public record EntryResponse(
            UUID id,
            UUID projectId,
            String title,
            String content,
            boolean active,
            boolean constantEnabled,
            boolean vectorEnabled,
            List<String> keywords,
            int priority,
            WorldbookScope scopeType,
            UUID scopeRefId,
            WorldbookVisibility visibilityType,
            UUID visibilityRefId,
            EmbeddingStatus embeddingStatus,
            String embeddingModel,
            long version,
            Instant createdAt,
            Instant updatedAt) {}
}
