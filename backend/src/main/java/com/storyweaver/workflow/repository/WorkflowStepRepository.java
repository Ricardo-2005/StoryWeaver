package com.storyweaver.workflow.repository;

import com.storyweaver.workflow.domain.WorkflowStep;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, UUID> {
    Optional<WorkflowStep> findByWorkflowRunIdAndStepName(UUID workflowRunId, String stepName);

    List<WorkflowStep> findAllByWorkflowRunIdOrderByStartedAtAsc(UUID workflowRunId);
}
