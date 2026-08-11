package com.storyweaver.consistency.repository;

import com.storyweaver.consistency.domain.FactStatus;
import com.storyweaver.consistency.domain.StoryFact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryFactRepository extends JpaRepository<StoryFact, UUID> {
    List<StoryFact> findAllByWorkflowRunIdOrderByCandidateIndexAsc(UUID workflowRunId);

    List<StoryFact> findAllByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, FactStatus status);

    void deleteAllByWorkflowRunId(UUID workflowRunId);

    Optional<StoryFact> findByProjectIdAndCreatedByAndMcpRequestKey(
            UUID projectId, UUID createdBy, String mcpRequestKey);
}
