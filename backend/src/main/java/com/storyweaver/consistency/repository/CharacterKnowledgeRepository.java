package com.storyweaver.consistency.repository;

import com.storyweaver.consistency.domain.CharacterKnowledge;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CharacterKnowledgeRepository extends JpaRepository<CharacterKnowledge, UUID> {
    Optional<CharacterKnowledge>
            findFirstByProjectIdAndCharacterIdAndFactKeyAndLifecycleStatusAndForgottenAtChapterNoIsNullOrderByLearnedAtChapterNoDesc(
                    UUID projectId, UUID characterId, String factKey, String lifecycleStatus);

    @Query(
            value =
                    """
                    SELECT * FROM character_knowledge
                    WHERE project_id=:projectId AND retrieval_eligible=TRUE AND lifecycle_status='ACTIVE'
                      AND learned_at_chapter_no<=:chapterNo
                      AND (forgotten_at_chapter_no IS NULL OR forgotten_at_chapter_no>:chapterNo)
                    ORDER BY learned_at_chapter_no DESC,updated_at DESC
                    """,
            nativeQuery = true)
    List<CharacterKnowledge> findCurrentAtChapter(
            @Param("projectId") UUID projectId, @Param("chapterNo") int chapterNo);

    List<CharacterKnowledge> findAllByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    List<CharacterKnowledge> findAllByProjectIdAndCharacterIdOrderByUpdatedAtDesc(UUID projectId, UUID characterId);
}
