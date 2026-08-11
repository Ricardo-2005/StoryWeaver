package com.storyweaver.branching.api;

import com.storyweaver.branching.application.ChapterBranchService;
import com.storyweaver.branching.application.ChapterBranchService.BranchView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChapterBranchController {
    private final ChapterBranchService service;

    public ChapterBranchController(ChapterBranchService service) {
        this.service = service;
    }

    @PostMapping("/chapters/{chapterId}/branches")
    ResponseEntity<BranchView> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID chapterId, @Valid @RequestBody CreateRequest r) {
        var value = service.create(
                chapterId, userId(jwt), r.name(), r.description(), r.title(), r.content(), r.changeSummary());
        return ResponseEntity.created(URI.create("/api/chapter-branches/" + value.id()))
                .body(value);
    }

    @GetMapping("/chapters/{chapterId}/branches")
    List<BranchView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID chapterId) {
        return service.list(chapterId, userId(jwt));
    }

    @GetMapping("/chapter-branches/{id}")
    BranchView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(id, userId(jwt));
    }

    @PostMapping("/chapter-branches/{id}/versions")
    BranchView version(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody VersionRequest r) {
        return service.addVersion(id, userId(jwt), r.expectedVersion(), r.title(), r.content(), r.changeSummary());
    }

    @PostMapping("/chapter-branches/{id}/promote-impact")
    BranchView promote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody VersionOnly r) {
        return service.promoteImpact(id, userId(jwt), r.expectedVersion());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record CreateRequest(
            @NotBlank String name, String description, String title, String content, String changeSummary) {}

    public record VersionRequest(
            long expectedVersion, @NotBlank String title, @NotBlank String content, String changeSummary) {}

    public record VersionOnly(long expectedVersion) {}
}
