package com.storyweaver.workflow.repository;

import com.storyweaver.workflow.domain.WorkflowRun;
import com.storyweaver.workflow.domain.WorkflowStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkflowRun> findWithLockById(UUID id);

    Optional<WorkflowRun> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    boolean existsByProjectIdAndStatusIn(UUID projectId, Collection<WorkflowStatus> statuses);

    List<WorkflowRun> findAllByStatusInAndHeartbeatAtBefore(
            Collection<WorkflowStatus> statuses, Instant heartbeatBefore);
}
