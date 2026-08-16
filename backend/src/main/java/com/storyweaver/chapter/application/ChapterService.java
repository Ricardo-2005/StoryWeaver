package com.storyweaver.chapter.application;

import com.storyweaver.chapter.domain.Chapter;
import com.storyweaver.chapter.domain.ChapterVersion;
import com.storyweaver.chapter.repository.ChapterRepository;
import com.storyweaver.chapter.repository.ChapterVersionRepository;
import com.storyweaver.evolution.application.ProjectEvolutionService;
import com.storyweaver.outline.domain.OutlineNode;
import com.storyweaver.outline.domain.OutlineNodeType;
import com.storyweaver.outline.repository.OutlineNodeRepository;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterService {
    private final ChapterRepository chapters;
    private final ChapterVersionRepository versions;
    private final OutlineNodeRepository outlines;
    private final ProjectAccessService projectAccess;
    private final ProjectEvolutionService evolution;
    private final Clock clock;

    public ChapterService(
            ChapterRepository chapters,
            ChapterVersionRepository versions,
            OutlineNodeRepository outlines,
            ProjectAccessService projectAccess,
            ProjectEvolutionService evolution,
            Clock clock) {
        this.chapters = chapters;
        this.versions = versions;
        this.outlines = outlines;
        this.projectAccess = projectAccess;
        this.evolution = evolution;
        this.clock = clock;
    }

    @Transactional
    public ChapterDetails create(
            UUID projectId, UUID ownerId, int chapterNo, String title, UUID outlineNodeId, String outline) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        validateChapterOutline(projectId, outlineNodeId);
        Chapter chapter = chapters.save(
                new Chapter(projectId, outlineNodeId, chapterNo, title.trim(), nullable(outline), clock.instant()));
        return new ChapterDetails(chapter, null);
    }

    @Transactional(readOnly = true)
    public List<ChapterDetails> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return chapters.findAllByProjectIdOrderByChapterNoAsc(projectId).stream()
                .map(this::details)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChapterDetails get(UUID chapterId, UUID ownerId) {
        return details(requireOwned(chapterId, ownerId));
    }

    @Transactional
    public ChapterDetails updateOutline(
            UUID chapterId, UUID ownerId, long expectedVersion, UUID outlineNodeId, String title, String outline) {
        Chapter chapter = requireOwned(chapterId, ownerId);
        requireVersion(chapter.getVersion(), expectedVersion);
        validateChapterOutline(chapter.getProjectId(), outlineNodeId);
        chapter.updateOutline(outlineNodeId, title.trim(), nullable(outline), clock.instant());
        chapters.flush();
        evolution.invalidate(chapter.getProjectId(), "CHAPTER", chapter.getId(), "CHAPTER_OUTLINE_CHANGED");
        return details(chapter);
    }

    @Transactional
    public ChapterDetails addVersion(
            UUID chapterId,
            UUID ownerId,
            long expectedVersion,
            String title,
            String content,
            String summary,
            String changeSummary) {
        Chapter chapter = requireOwned(chapterId, ownerId);
        requireVersion(chapter.getVersion(), expectedVersion);
        var now = clock.instant();
        int versionNo = chapter.addVersion(title.trim(), now);
        ChapterVersion version = versions.save(new ChapterVersion(
                chapter.getProjectId(),
                chapter.getId(),
                versionNo,
                title.trim(),
                content,
                nullable(summary),
                nullable(changeSummary),
                null,
                ownerId,
                now));
        chapters.flush();
        evolution.invalidate(chapter.getProjectId(), "CHAPTER", chapter.getId(), "CHAPTER_CONTENT_CHANGED");
        return new ChapterDetails(chapter, version);
    }

    @Transactional(readOnly = true)
    public List<ChapterVersion> versions(UUID chapterId, UUID ownerId) {
        Chapter chapter = requireOwned(chapterId, ownerId);
        return versions.findAllByChapterIdOrderByVersionNoDesc(chapter.getId());
    }

    @Transactional
    public ChapterDetails restore(
            UUID chapterId, int sourceVersionNo, UUID ownerId, long expectedVersion, String changeSummary) {
        Chapter chapter = requireOwned(chapterId, ownerId);
        requireVersion(chapter.getVersion(), expectedVersion);
        ChapterVersion source = versions.findByChapterIdAndVersionNo(chapterId, sourceVersionNo)
                .orElseThrow(() -> new NotFoundException("chapter_version_not_found", "Chapter version was not found"));
        var now = clock.instant();
        int newVersionNo = chapter.addVersion(source.getTitle(), now);
        String summary = nullable(changeSummary);
        if (summary == null) summary = "Restored from version " + sourceVersionNo;
        ChapterVersion restored = versions.save(new ChapterVersion(
                chapter.getProjectId(),
                chapter.getId(),
                newVersionNo,
                source.getTitle(),
                source.getContent(),
                source.getSummary(),
                summary,
                sourceVersionNo,
                ownerId,
                now));
        chapters.flush();
        evolution.invalidate(chapter.getProjectId(), "CHAPTER", chapter.getId(), "CHAPTER_VERSION_RESTORED");
        return new ChapterDetails(chapter, restored);
    }

    private ChapterDetails details(Chapter chapter) {
        if (chapter.getCurrentVersionNo() == 0) return new ChapterDetails(chapter, null);
        ChapterVersion current = versions.findByChapterIdAndVersionNo(chapter.getId(), chapter.getCurrentVersionNo())
                .orElseThrow(() -> new IllegalStateException("Current chapter version is missing"));
        return new ChapterDetails(chapter, current);
    }

    private Chapter requireOwned(UUID chapterId, UUID ownerId) {
        Chapter chapter = chapters.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("chapter_not_found", "Chapter was not found"));
        projectAccess.requireOwnedProject(chapter.getProjectId(), ownerId);
        return chapter;
    }

    private void validateChapterOutline(UUID projectId, UUID outlineNodeId) {
        if (outlineNodeId == null) return;
        OutlineNode node = outlines.findById(outlineNodeId)
                .orElseThrow(() -> new NotFoundException("outline_not_found", "Chapter outline was not found"));
        if (!node.getProjectId().equals(projectId) || node.getNodeType() != OutlineNodeType.CHAPTER) {
            throw new NotFoundException("outline_not_found", "Chapter outline was not found");
        }
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new ConflictException("optimistic_lock_conflict", "The chapter changed; reload it before retrying");
        }
    }

    private String nullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ChapterDetails(Chapter chapter, ChapterVersion currentVersion) {}
}
