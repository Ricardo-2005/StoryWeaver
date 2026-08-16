package com.storyweaver.worldbook.api;

import com.storyweaver.worldbook.api.WorldbookDtos.CreateEntryRequest;
import com.storyweaver.worldbook.api.WorldbookDtos.EntryResponse;
import com.storyweaver.worldbook.api.WorldbookDtos.PreviewRequest;
import com.storyweaver.worldbook.api.WorldbookDtos.UpdateEntryRequest;
import com.storyweaver.worldbook.application.WorldbookService;
import com.storyweaver.worldbook.application.WorldbookService.ActivationPreview;
import com.storyweaver.worldbook.application.WorldbookService.EntryValues;
import com.storyweaver.worldbook.domain.WorldbookEntry;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Arrays;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WorldbookController {
    private final WorldbookService service;

    public WorldbookController(WorldbookService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/worldbook-entries")
    ResponseEntity<EntryResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateEntryRequest request) {
        WorldbookEntry entry = service.create(projectId, userId(jwt), values(request));
        return ResponseEntity.created(URI.create("/api/worldbook-entries/" + entry.getId()))
                .body(response(entry));
    }

    @GetMapping("/projects/{projectId}/worldbook-entries")
    List<EntryResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt)).stream().map(this::response).toList();
    }

    @PutMapping("/worldbook-entries/{entryId}")
    EntryResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID entryId,
            @Valid @RequestBody UpdateEntryRequest request) {
        return response(service.update(entryId, userId(jwt), request.expectedVersion(), values(request)));
    }

    @DeleteMapping("/worldbook-entries/{entryId}")
    ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entryId) {
        service.cancel(entryId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/worldbook/preview")
    ActivationPreview preview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody PreviewRequest request) {
        return service.preview(
                projectId,
                userId(jwt),
                request.query(),
                request.chapterId(),
                request.viewpointCharacterId(),
                request.tokenBudget(),
                request.topK());
    }

    private EntryValues values(CreateEntryRequest request) {
        return new EntryValues(
                request.title(),
                request.content(),
                request.active(),
                request.constantEnabled(),
                request.vectorEnabled(),
                request.keywords(),
                request.priority(),
                request.scopeType(),
                request.scopeRefId(),
                request.visibilityType(),
                request.visibilityRefId());
    }

    private EntryValues values(UpdateEntryRequest request) {
        return new EntryValues(
                request.title(),
                request.content(),
                request.active(),
                request.constantEnabled(),
                request.vectorEnabled(),
                request.keywords(),
                request.priority(),
                request.scopeType(),
                request.scopeRefId(),
                request.visibilityType(),
                request.visibilityRefId());
    }

    private EntryResponse response(WorldbookEntry entry) {
        return new EntryResponse(
                entry.getId(),
                entry.getProjectId(),
                entry.getTitle(),
                entry.getContent(),
                entry.isActive(),
                entry.isConstantEnabled(),
                entry.isVectorEnabled(),
                Arrays.asList(entry.getKeywords()),
                entry.getPriority(),
                entry.getScopeType(),
                entry.getScopeRefId(),
                entry.getVisibilityType(),
                entry.getVisibilityRefId(),
                entry.getEmbeddingStatus(),
                entry.getEmbeddingModel(),
                entry.getVersion(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
