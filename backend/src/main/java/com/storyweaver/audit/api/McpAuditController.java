package com.storyweaver.audit.api;

import com.storyweaver.audit.application.McpAuditService;
import com.storyweaver.audit.domain.McpAuditLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class McpAuditController {
    private final McpAuditService service;

    public McpAuditController(McpAuditService service) {
        this.service = service;
    }

    @GetMapping("/projects/{projectId}/mcp-audit")
    List<AuditResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, UUID.fromString(jwt.getSubject())).stream()
                .map(this::response)
                .toList();
    }

    private AuditResponse response(McpAuditLog value) {
        return new AuditResponse(
                value.getId(),
                value.getCallerUserId(),
                value.getProjectId(),
                value.getOperationType(),
                value.getOperationName(),
                value.getRequestId(),
                value.getOutcome(),
                value.getErrorCode(),
                value.getDurationMillis(),
                value.getCreatedAt());
    }

    public record AuditResponse(
            UUID id,
            UUID callerUserId,
            UUID projectId,
            String operationType,
            String operationName,
            String requestId,
            String outcome,
            String errorCode,
            long durationMillis,
            Instant createdAt) {}
}
