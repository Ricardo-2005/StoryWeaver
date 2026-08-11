package com.storyweaver.chapter.api;

import com.storyweaver.chapter.api.ChapterDtos.ChapterResponse;
import com.storyweaver.chapter.api.ChapterDtos.ChapterVersionResponse;
import com.storyweaver.chapter.api.ChapterDtos.CreateChapterRequest;
import com.storyweaver.chapter.api.ChapterDtos.CreateChapterVersionRequest;
import com.storyweaver.chapter.api.ChapterDtos.RestoreChapterVersionRequest;
import com.storyweaver.chapter.api.ChapterDtos.UpdateChapterOutlineRequest;
import com.storyweaver.chapter.application.ChapterService;
import com.storyweaver.chapter.application.ChapterService.ChapterDetails;
import com.storyweaver.chapter.domain.ChapterVersion;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChapterController {
    private final ChapterService service;

    public ChapterController(ChapterService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/chapters")
    ResponseEntity<ChapterResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateChapterRequest request) {
        ChapterDetails details = service.create(
                projectId,
                userId(jwt),
                request.chapterNo(),
                request.title(),
                request.outlineNodeId(),
                request.outline());
        return ResponseEntity.created(
                        URI.create("/api/chapters/" + details.chapter().getId()))
                .body(response(details));
    }

    @GetMapping("/projects/{projectId}/chapters")
    List<ChapterResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt)).stream().map(this::response).toList();
    }

    @GetMapping("/chapters/{chapterId}")
    ChapterResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID chapterId) {
        return response(service.get(chapterId, userId(jwt)));
    }

    @PutMapping("/chapters/{chapterId}/outline")
    ChapterResponse updateOutline(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chapterId,
            @Valid @RequestBody UpdateChapterOutlineRequest request) {
        return response(service.updateOutline(
                chapterId,
                userId(jwt),
                request.expectedVersion(),
                request.outlineNodeId(),
                request.title(),
                request.outline()));
    }

    @PostMapping("/chapters/{chapterId}/versions")
    ResponseEntity<ChapterResponse> addVersion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chapterId,
            @Valid @RequestBody CreateChapterVersionRequest request) {
        ChapterDetails details = service.addVersion(
                chapterId,
                userId(jwt),
                request.expectedVersion(),
                request.title(),
                request.content(),
                request.summary(),
                request.changeSummary());
        return ResponseEntity.created(URI.create("/api/chapters/" + chapterId + "/versions/"
                        + details.currentVersion().getVersionNo()))
                .body(response(details));
    }

    @GetMapping("/chapters/{chapterId}/versions")
    List<ChapterVersionResponse> versions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID chapterId) {
        return service.versions(chapterId, userId(jwt)).stream()
                .map(this::versionResponse)
                .toList();
    }

    @PostMapping("/chapters/{chapterId}/restore/{versionNo}")
    ChapterResponse restore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chapterId,
            @PathVariable int versionNo,
            @Valid @RequestBody RestoreChapterVersionRequest request) {
        return response(
                service.restore(chapterId, versionNo, userId(jwt), request.expectedVersion(), request.changeSummary()));
    }

    private ChapterResponse response(ChapterDetails details) {
        var c = details.chapter();
        return new ChapterResponse(
                c.getId(),
                c.getProjectId(),
                c.getOutlineNodeId(),
                c.getChapterNo(),
                c.getTitle(),
                c.getOutline(),
                c.getStatus(),
                c.getCurrentVersionNo(),
                c.getVersion(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                details.currentVersion() == null ? null : versionResponse(details.currentVersion()));
    }

    private ChapterVersionResponse versionResponse(ChapterVersion v) {
        return new ChapterVersionResponse(
                v.getId(),
                v.getChapterId(),
                v.getVersionNo(),
                v.getTitle(),
                v.getContent(),
                v.getSummary(),
                v.getChangeSummary(),
                v.getRestoredFromVersionNo(),
                v.getCreatedAt());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
