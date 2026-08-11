package com.storyweaver.workflow.repository;

import com.storyweaver.workflow.domain.ContextPacket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContextPacketRepository extends JpaRepository<ContextPacket, UUID> {
    Optional<ContextPacket> findByWorkflowRunId(UUID workflowRunId);
}
