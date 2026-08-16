package com.storyweaver.project.api;

import com.storyweaver.project.domain.LengthType;
import com.storyweaver.project.domain.NarrativePerspective;
import com.storyweaver.project.domain.TargetAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {}

    public record CreateProjectRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 80) String genre,
            @Size(max = 20) String customGenre,
            @NotNull TargetAudience targetAudience,
            @NotNull NarrativePerspective narrativePerspective,
            @NotNull LengthType lengthType,
            @NotBlank @Size(min = 10, max = 500) String premise,
            @Size(max = 300) String description,
            @Size(max = 3000) String authorIntent,
            @Size(max = 2000) String currentFocus,
            @NotNull List<@NotBlank @Size(max = 500) String> worldRules,
            @Positive Integer targetWordCount,
            @Positive Integer chapterWordTarget,
            UUID baseSkillVersionId) {}

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 80) String genre,
            @Size(max = 20) String customGenre,
            @NotNull TargetAudience targetAudience,
            @NotNull NarrativePerspective narrativePerspective,
            @NotNull LengthType lengthType,
            @NotBlank @Size(min = 10, max = 500) String premise,
            @Size(max = 300) String description,
            @Size(max = 3000) String authorIntent,
            @Size(max = 2000) String currentFocus,
            @NotNull List<@NotBlank @Size(max = 500) String> worldRules,
            @Positive Integer targetWordCount,
            @Positive Integer chapterWordTarget,
            boolean archived,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record SnapshotRequest(@NotNull @PositiveOrZero Long expectedVersion) {}

    public record ProjectResponse(
            UUID id,
            String name,
            String genre,
            String customGenre,
            TargetAudience targetAudience,
            NarrativePerspective narrativePerspective,
            LengthType lengthType,
            String premise,
            String description,
            String authorIntent,
            String currentFocus,
            List<String> worldRules,
            Integer targetWordCount,
            Integer chapterWordTarget,
            boolean archived,
            String creationSource,
            String reconstructionStatus,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record SnapshotResponse(UUID id, UUID projectId, long projectVersion, Instant createdAt) {}
}
