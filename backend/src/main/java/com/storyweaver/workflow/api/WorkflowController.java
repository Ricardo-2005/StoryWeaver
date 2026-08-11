package com.storyweaver.workflow.api;

import com.storyweaver.workflow.api.WorkflowDtos.ApproveWorkflowRequest;
import com.storyweaver.workflow.api.WorkflowDtos.LocalRevisionRequest;
import com.storyweaver.workflow.api.WorkflowDtos.RevisionRequest;
import com.storyweaver.workflow.api.WorkflowDtos.StartWorkflowRequest;
import com.storyweaver.workflow.api.WorkflowDtos.WorkflowResponse;
import com.storyweaver.workflow.application.WorkflowService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class WorkflowController {
    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @PostMapping("/chapters/{chapterId}/workflows")
    ResponseEntity<WorkflowResponse> start(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID chapterId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StartWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.start(
                        chapterId, userId(jwt), idempotencyKey, request.viewpointCharacterId(), request.instruction()));
    }

    @GetMapping("/workflows/{runId}")
    WorkflowResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return service.get(runId, userId(jwt));
    }

    @GetMapping(value = "/workflows/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(defaultValue = "0") long afterEventId) {
        long cursor = lastEventId == null || lastEventId.isBlank() ? afterEventId : parseEventId(lastEventId);
        return service.events(runId, userId(jwt), cursor);
    }

    @PostMapping("/workflows/{runId}/cancel")
    WorkflowResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return service.cancel(runId, userId(jwt));
    }

    @PostMapping("/workflows/{runId}/approve")
    WorkflowResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId,
            @Valid @RequestBody ApproveWorkflowRequest request) {
        return service.approve(runId, userId(jwt), request);
    }

    @PostMapping("/workflows/{runId}/request-revision")
    WorkflowResponse requestRevision(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId, @Valid @RequestBody RevisionRequest request) {
        return service.reextract(runId, userId(jwt), request.revisedDraft());
    }

    @PostMapping("/workflows/{runId}/reextract")
    WorkflowResponse reextract(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId, @Valid @RequestBody RevisionRequest request) {
        return service.reextract(runId, userId(jwt), request.revisedDraft());
    }

    @PostMapping("/workflows/{runId}/local-revisions")
    WorkflowResponse localRevision(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId,
            @Valid @RequestBody LocalRevisionRequest request) {
        return service.localRevision(
                runId,
                userId(jwt),
                request.expectedVersion(),
                request.startOffset(),
                request.endOffset(),
                request.replacement(),
                request.reason());
    }

    private long parseEventId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
