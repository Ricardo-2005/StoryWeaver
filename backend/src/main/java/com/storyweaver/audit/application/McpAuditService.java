package com.storyweaver.audit.application;

import com.storyweaver.audit.domain.McpAuditLog;
import com.storyweaver.audit.repository.McpAuditLogRepository;
import com.storyweaver.project.application.ProjectAccessService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class McpAuditService {
    private final McpAuditLogRepository logs;
    private final ProjectAccessService projectAccess;
    private final Clock clock;

    public McpAuditService(McpAuditLogRepository logs, ProjectAccessService projectAccess, Clock clock) {
        this.logs = logs;
        this.projectAccess = projectAccess;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID callerId,
            UUID projectId,
            String type,
            String name,
            String requestId,
            String outcome,
            String errorCode,
            long durationMillis) {
        logs.save(new McpAuditLog(
                callerId, projectId, type, name, requestId, outcome, errorCode, durationMillis, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<McpAuditLog> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return logs.findAllByProjectIdOrderByCreatedAtDesc(projectId);
    }
}
