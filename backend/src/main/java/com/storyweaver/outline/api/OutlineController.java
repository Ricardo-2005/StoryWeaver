package com.storyweaver.outline.api;

import com.storyweaver.outline.api.OutlineDtos.CreateOutlineRequest;
import com.storyweaver.outline.api.OutlineDtos.OutlineResponse;
import com.storyweaver.outline.api.OutlineDtos.UpdateOutlineRequest;
import com.storyweaver.outline.application.OutlineService;
import com.storyweaver.outline.domain.OutlineNode;
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
public class OutlineController {
    private final OutlineService service;

    public OutlineController(OutlineService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/outlines")
    ResponseEntity<OutlineResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateOutlineRequest request) {
        OutlineNode outline = service.create(
                projectId,
                userId(jwt),
                request.parentId(),
                request.nodeType(),
                request.title(),
                request.summary(),
                request.objective(),
                request.sequenceNo());
        return ResponseEntity.created(URI.create("/api/outlines/" + outline.getId()))
                .body(response(outline));
    }

    @GetMapping("/projects/{projectId}/outlines")
    List<OutlineResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt)).stream().map(this::response).toList();
    }

    @GetMapping("/outlines/{outlineId}")
    OutlineResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID outlineId) {
        return response(service.get(outlineId, userId(jwt)));
    }

    @PutMapping("/outlines/{outlineId}")
    OutlineResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID outlineId,
            @Valid @RequestBody UpdateOutlineRequest request) {
        return response(service.update(
                outlineId,
                userId(jwt),
                request.expectedVersion(),
                request.title(),
                request.summary(),
                request.objective(),
                request.sequenceNo()));
    }

    private OutlineResponse response(OutlineNode o) {
        return new OutlineResponse(
                o.getId(),
                o.getProjectId(),
                o.getParentId(),
                o.getNodeType(),
                o.getTitle(),
                o.getSummary(),
                o.getObjective(),
                o.getSequenceNo(),
                o.getVersion(),
                o.getCreatedAt(),
                o.getUpdatedAt());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
