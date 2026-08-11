package com.storyweaver.outline.api;

import com.storyweaver.outline.domain.OutlineNodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OutlineDtos {
    private OutlineDtos() {}

    public record CreateOutlineRequest(
            UUID parentId,
            @NotNull OutlineNodeType nodeType,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 50000) String summary,
            @Size(max = 50000) String objective,
            @PositiveOrZero int sequenceNo) {}

    public record UpdateOutlineRequest(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 50000) String summary,
            @Size(max = 50000) String objective,
            @PositiveOrZero int sequenceNo,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record OutlineResponse(
            UUID id,
            UUID projectId,
            UUID parentId,
            OutlineNodeType nodeType,
            String title,
            String summary,
            String objective,
            int sequenceNo,
            long version,
            Instant createdAt,
            Instant updatedAt) {}
}
