package com.storyweaver.workflow.repository;

import com.storyweaver.workflow.domain.WorkflowEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, Long> {
    List<WorkflowEvent> findTop200ByWorkflowRunIdAndEventIdGreaterThanOrderByEventIdAsc(
            UUID workflowRunId, long eventId);
}
