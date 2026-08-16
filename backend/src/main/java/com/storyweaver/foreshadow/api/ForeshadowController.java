package com.storyweaver.foreshadow.api;

import com.storyweaver.foreshadow.application.ForeshadowService;
import com.storyweaver.foreshadow.application.ForeshadowService.ForeshadowInput;
import com.storyweaver.foreshadow.application.ForeshadowService.ForeshadowView;
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
public class ForeshadowController {
    private final ForeshadowService service;

    public ForeshadowController(ForeshadowService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/foreshadows")
    ResponseEntity<ForeshadowView> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody CreateRequest request) {
        var value = service.create(projectId, userId(jwt), request.input());
        return ResponseEntity.created(URI.create("/api/foreshadows/" + value.id()))
                .body(value);
    }

    @GetMapping("/projects/{projectId}/foreshadows")
    List<ForeshadowView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt));
    }

    @PutMapping("/foreshadows/{id}")
    ForeshadowView update(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, userId(jwt), request.expectedVersion(), request.input());
    }

    @PostMapping("/foreshadows/{id}/transition")
    ForeshadowView transition(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
        return service.transition(
                id, userId(jwt), request.expectedVersion(), request.status(), request.resolvedChapterId());
    }

    @DeleteMapping("/foreshadows/{id}")
    ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.cancel(id, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public record CreateRequest(
            @NotBlank String title, String description, UUID plantedChapterId, Integer targetChapterNo, String notes) {
        ForeshadowInput input() {
            return new ForeshadowInput(title, description, plantedChapterId, targetChapterNo, notes);
        }
    }

    public record UpdateRequest(
            long expectedVersion,
            @NotBlank String title,
            String description,
            UUID plantedChapterId,
            Integer targetChapterNo,
            String notes) {
        ForeshadowInput input() {
            return new ForeshadowInput(title, description, plantedChapterId, targetChapterNo, notes);
        }
    }

    public record TransitionRequest(long expectedVersion, @NotBlank String status, UUID resolvedChapterId) {}
}
