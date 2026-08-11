package com.storyweaver.project.api;

import com.storyweaver.project.api.ProjectDtos.CreateProjectRequest;
import com.storyweaver.project.api.ProjectDtos.ProjectResponse;
import com.storyweaver.project.api.ProjectDtos.SnapshotRequest;
import com.storyweaver.project.api.ProjectDtos.SnapshotResponse;
import com.storyweaver.project.api.ProjectDtos.UpdateProjectRequest;
import com.storyweaver.project.application.ProjectService;
import com.storyweaver.project.domain.NovelProject;
import com.storyweaver.project.domain.ProjectSnapshot;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    ResponseEntity<ProjectResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProjectRequest request) {
        NovelProject project = projectService.create(
                userId(jwt),
                request.name(),
                request.genre(),
                request.customGenre(),
                request.targetAudience(),
                request.narrativePerspective(),
                request.lengthType(),
                request.premise(),
                request.description(),
                request.authorIntent(),
                request.currentFocus(),
                request.worldRules(),
                request.targetWordCount(),
                request.chapterWordTarget(),
                request.baseSkillVersionId());
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId()))
                .body(toResponse(project));
    }

    @GetMapping
    List<ProjectResponse> list(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "false") boolean includeArchived) {
        return projectService.list(userId(jwt), includeArchived).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{projectId}")
    ProjectResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return toResponse(projectService.get(projectId, userId(jwt)));
    }

    @PutMapping("/{projectId}")
    ProjectResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return toResponse(projectService.update(
                projectId,
                userId(jwt),
                request.expectedVersion(),
                request.name(),
                request.genre(),
                request.customGenre(),
                request.targetAudience(),
                request.narrativePerspective(),
                request.lengthType(),
                request.premise(),
                request.description(),
                request.authorIntent(),
                request.currentFocus(),
                request.worldRules(),
                request.targetWordCount(),
                request.chapterWordTarget(),
                request.archived()));
    }

    @DeleteMapping("/{projectId}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @RequestParam long expectedVersion) {
        projectService.delete(projectId, userId(jwt), expectedVersion);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/snapshots")
    ResponseEntity<SnapshotResponse> snapshot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody SnapshotRequest request) {
        ProjectSnapshot snapshot = projectService.snapshot(projectId, userId(jwt), request.expectedVersion());
        SnapshotResponse response = new SnapshotResponse(
                snapshot.getId(), snapshot.getProjectId(), snapshot.getProjectVersion(), snapshot.getCreatedAt());
        return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/snapshots/" + snapshot.getId()))
                .body(response);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private ProjectResponse toResponse(NovelProject project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getGenre(),
                project.getCustomGenre(),
                project.getTargetAudience(),
                project.getNarrativePerspective(),
                project.getLengthType(),
                project.getPremise(),
                project.getDescription(),
                project.getAuthorIntent(),
                project.getCurrentFocus(),
                List.of(project.getWorldRules()),
                project.getTargetWordCount(),
                project.getChapterWordTarget(),
                project.isArchived(),
                project.getVersion(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
