package com.storyweaver.audit.repository;

import com.storyweaver.audit.domain.McpAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpAuditLogRepository extends JpaRepository<McpAuditLog, UUID> {
    List<McpAuditLog> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
