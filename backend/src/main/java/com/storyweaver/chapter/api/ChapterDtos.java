package com.storyweaver.chapter.api;

import com.storyweaver.chapter.domain.ChapterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ChapterDtos {
    private ChapterDtos() {}

    public record CreateChapterRequest(
            @Positive int chapterNo,
            @NotBlank @Size(max = 160) String title,
            UUID outlineNodeId,
            @Size(max = 50000) String outline) {}

    public record UpdateChapterOutlineRequest(
            UUID outlineNodeId,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 50000) String outline,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record CreateChapterVersionRequest(
            @NotBlank @Size(max = 160) String title,
            @NotNull @Size(max = 500000) String content,
            @Size(max = 50000) String summary,
            @Size(max = 500) String changeSummary,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record RestoreChapterVersionRequest(
            @Size(max = 500) String changeSummary, @NotNull @PositiveOrZero Long expectedVersion) {}

    public record ChapterVersionResponse(
            UUID id,
            UUID chapterId,
            int versionNo,
            String title,
            String content,
            String summary,
            String changeSummary,
            Integer restoredFromVersionNo,
            Instant createdAt) {}

    public record ChapterResponse(
            UUID id,
            UUID projectId,
            UUID outlineNodeId,
            int chapterNo,
            String title,
            String outline,
            ChapterStatus status,
            int currentVersionNo,
            long version,
            Instant createdAt,
            Instant updatedAt,
            ChapterVersionResponse currentVersion) {}
}
