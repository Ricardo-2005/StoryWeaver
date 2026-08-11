package com.storyweaver.project.application;

import com.storyweaver.project.domain.LengthType;
import com.storyweaver.project.domain.NarrativePerspective;
import com.storyweaver.project.domain.NovelProject;
import com.storyweaver.project.domain.ProjectSnapshot;
import com.storyweaver.project.domain.TargetAudience;
import com.storyweaver.project.repository.NovelProjectRepository;
import com.storyweaver.project.repository.ProjectSnapshotRepository;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.shared.event.ProjectCreatedEvent;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final NovelProjectRepository projects;
    private final ProjectSnapshotRepository snapshots;
    private final List<ProjectSnapshotContributor> snapshotContributors;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProjectService(
            NovelProjectRepository projects,
            ProjectSnapshotRepository snapshots,
            List<ProjectSnapshotContributor> snapshotContributors,
            ApplicationEventPublisher events,
            Clock clock) {
        this.projects = projects;
        this.snapshots = snapshots;
        this.snapshotContributors = List.copyOf(snapshotContributors);
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public NovelProject create(
            UUID ownerId,
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
            UUID baseSkillVersionId) {
        validateGenre(genre, customGenre);
        NovelProject project = new NovelProject(
                ownerId,
                name.trim(),
                genre.trim(),
                nullable(customGenre),
                targetAudience,
                narrativePerspective,
                lengthType,
                premise.trim(),
                nullable(description),
                nullable(authorIntent),
                nullable(currentFocus),
                normalizeWorldRules(worldRules),
                targetWordCount,
                chapterWordTarget,
                clock.instant());
        NovelProject saved = projects.save(project);
        events.publishEvent(new ProjectCreatedEvent(saved.getId(), ownerId, baseSkillVersionId));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NovelProject> list(UUID ownerId, boolean includeArchived) {
        return includeArchived
                ? projects.findAllByOwnerIdOrderByUpdatedAtDesc(ownerId)
                : projects.findAllByOwnerIdAndArchivedFalseOrderByUpdatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public NovelProject get(UUID projectId, UUID ownerId) {
        return requireOwned(projectId, ownerId);
    }

    @Transactional
    public NovelProject update(
            UUID projectId,
            UUID ownerId,
            long expectedVersion,
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
            boolean archived) {
        NovelProject project = requireOwned(projectId, ownerId);
        requireVersion(project.getVersion(), expectedVersion);
        validateGenre(genre, customGenre);
        project.update(
                name.trim(),
                genre.trim(),
                nullable(customGenre),
                targetAudience,
                narrativePerspective,
                lengthType,
                premise.trim(),
                nullable(description),
                nullable(authorIntent),
                nullable(currentFocus),
                normalizeWorldRules(worldRules),
                targetWordCount,
                chapterWordTarget,
                archived,
                clock.instant());
        return project;
    }

    @Transactional
    public void delete(UUID projectId, UUID ownerId, long expectedVersion) {
        NovelProject project = requireOwned(projectId, ownerId);
        requireVersion(project.getVersion(), expectedVersion);
        if (!project.isArchived()) {
            throw new ConflictException("project_not_archived", "Archive the project before deleting it permanently");
        }
        projects.delete(project);
        projects.flush();
    }

    @Transactional
    public ProjectSnapshot snapshot(UUID projectId, UUID ownerId, long expectedVersion) {
        NovelProject project = requireOwned(projectId, ownerId);
        requireVersion(project.getVersion(), expectedVersion);
        Map<String, Object> projectData = new LinkedHashMap<>();
        projectData.put("id", project.getId());
        projectData.put("name", project.getName());
        projectData.put("genre", project.getGenre());
        projectData.put("customGenre", project.getCustomGenre());
        projectData.put("targetAudience", project.getTargetAudience());
        projectData.put("narrativePerspective", project.getNarrativePerspective());
        projectData.put("lengthType", project.getLengthType());
        projectData.put("premise", project.getPremise());
        projectData.put("description", project.getDescription());
        projectData.put("authorIntent", project.getAuthorIntent());
        projectData.put("currentFocus", project.getCurrentFocus());
        projectData.put("worldRules", project.getWorldRules());
        projectData.put("targetWordCount", project.getTargetWordCount());
        projectData.put("chapterWordTarget", project.getChapterWordTarget());
        projectData.put("archived", project.isArchived());
        projectData.put("version", project.getVersion());

        Map<String, Object> snapshotData = new LinkedHashMap<>();
        snapshotData.put("project", projectData);
        for (ProjectSnapshotContributor contributor : snapshotContributors) {
            snapshotData.put(contributor.sectionName(), contributor.contribute(projectId));
        }
        return snapshots.save(
                new ProjectSnapshot(projectId, ownerId, project.getVersion(), snapshotData, clock.instant()));
    }

    private NovelProject requireOwned(UUID projectId, UUID ownerId) {
        return projects.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new NotFoundException("project_not_found", "Project was not found"));
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new ConflictException("optimistic_lock_conflict", "The project changed; reload it before retrying");
        }
    }

    private String nullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateGenre(String genre, String customGenre) {
        boolean isCustom = "CUSTOM".equals(genre.trim());
        if (isCustom && nullable(customGenre) == null) {
            throw new com.storyweaver.shared.error.BadRequestException(
                    "custom_genre_required", "A custom genre name is required when genre is CUSTOM");
        }
        if (!isCustom && nullable(customGenre) != null) {
            throw new com.storyweaver.shared.error.BadRequestException(
                    "custom_genre_not_allowed", "customGenre is only allowed when genre is CUSTOM");
        }
    }

    private String[] normalizeWorldRules(List<String> values) {
        return values.stream().map(String::trim).toArray(String[]::new);
    }
}
