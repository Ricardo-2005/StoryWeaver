package com.storyweaver.consistency.repository;

import com.storyweaver.consistency.domain.ReviewIssue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewIssueRepository extends JpaRepository<ReviewIssue, UUID> {
    List<ReviewIssue> findAllByWorkflowRunIdOrderByCreatedAtAsc(UUID workflowRunId);

    boolean existsByWorkflowRunIdAndBlockingTrueAndResolvedFalse(UUID workflowRunId);

    void deleteAllByWorkflowRunId(UUID workflowRunId);
}
