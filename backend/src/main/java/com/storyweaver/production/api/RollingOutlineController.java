package com.storyweaver.production.api;

import com.storyweaver.production.application.RollingOutlineService;
import com.storyweaver.production.application.RollingOutlineService.RollingOutlineView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/rolling-outline")
public class RollingOutlineController {
    private final RollingOutlineService service;

    public RollingOutlineController(RollingOutlineService service) {
        this.service = service;
    }

    @GetMapping
    RollingOutlineView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.get(projectId, userId(jwt));
    }

    @PutMapping
    RollingOutlineView put(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody PutRequest r) {
        return service.put(
                projectId,
                userId(jwt),
                r.expectedVersion(),
                r.currentChapterNo(),
                r.windowSize(),
                r.summary(),
                r.goals(),
                r.risks());
    }

    @PostMapping("/advance")
    RollingOutlineView advance(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody AdvanceRequest r) {
        return service.advance(projectId, userId(jwt), r.expectedVersion(), r.summary(), r.goals(), r.risks());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record PutRequest(
            long expectedVersion,
            @Min(1) int currentChapterNo,
            @Min(1) @Max(20) int windowSize,
            String summary,
            @NotNull List<String> goals,
            @NotNull List<String> risks) {}

    public record AdvanceRequest(
            long expectedVersion, String summary, @NotNull List<String> goals, @NotNull List<String> risks) {}
}
