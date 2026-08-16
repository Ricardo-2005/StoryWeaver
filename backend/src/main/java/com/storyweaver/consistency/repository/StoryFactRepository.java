package com.storyweaver.consistency.repository;

import com.storyweaver.consistency.domain.FactStatus;
import com.storyweaver.consistency.domain.StoryFact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryFactRepository extends JpaRepository<StoryFact, UUID> {
    List<StoryFact> findAllByWorkflowRunIdOrderByCandidateIndexAsc(UUID workflowRunId);

    List<StoryFact> findAllByProjectIdAndStatusOrderByCreatedAtDesc(UUID projectId, FactStatus status);

    @Query(
            value =
                    """
                    SELECT * FROM story_fact
                    WHERE project_id=:projectId AND status='ACCEPTED' AND retrieval_eligible=TRUE
                      AND lifecycle_status='ACTIVE' AND valid_from_chapter_no<=:chapterNo
                      AND (valid_to_chapter_no IS NULL OR valid_to_chapter_no>:chapterNo)
                    ORDER BY valid_from_chapter_no DESC,created_at DESC
                    """,
            nativeQuery = true)
    List<StoryFact> findCurrentAtChapter(@Param("projectId") UUID projectId, @Param("chapterNo") int chapterNo);

    void deleteAllByWorkflowRunId(UUID workflowRunId);

    Optional<StoryFact> findByProjectIdAndCreatedByAndMcpRequestKey(
            UUID projectId, UUID createdBy, String mcpRequestKey);
}
