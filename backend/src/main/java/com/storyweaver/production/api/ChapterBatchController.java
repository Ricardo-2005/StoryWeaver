package com.storyweaver.production.api;

import com.storyweaver.production.application.ChapterBatchService;
import com.storyweaver.production.application.ChapterBatchService.BatchView;
import com.storyweaver.production.application.ChapterBatchService.GateView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChapterBatchController {
    private final ChapterBatchService service;

    public ChapterBatchController(ChapterBatchService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/chapter-batches")
    ResponseEntity<BatchView> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody CreateRequest r) {
        var value = service.create(
                projectId, userId(jwt), r.viewpointCharacterId(), r.instruction(), r.chapterIds(), r.gatedChapterIds());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/chapter-batches/" + value.id()))
                .body(value);
    }

    @GetMapping("/projects/{projectId}/chapter-batches")
    List<BatchView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt));
    }

    @GetMapping("/chapter-batches/{id}")
    BatchView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(id, userId(jwt));
    }

    @PostMapping("/chapter-batches/{id}/pause")
    BatchView pause(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.pause(id, userId(jwt));
    }

    @PostMapping("/chapter-batches/{id}/resume")
    BatchView resume(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.resume(id, userId(jwt));
    }

    @PostMapping("/chapter-batches/{id}/cancel")
    BatchView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.cancel(id, userId(jwt));
    }

    @GetMapping("/chapter-batches/{id}/gates")
    List<GateView> gates(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.gates(id, userId(jwt));
    }

    @PostMapping("/story-gates/{id}/approve")
    GateView approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.decideGate(id, userId(jwt), true);
    }

    @PostMapping("/story-gates/{id}/reject")
    GateView reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.decideGate(id, userId(jwt), false);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record CreateRequest(
            @NotNull UUID viewpointCharacterId,
            @NotBlank String instruction,
            @NotEmpty @Size(max = 3) List<UUID> chapterIds,
            List<UUID> gatedChapterIds) {}
}
