package com.storyweaver.consistency.repository;

import com.storyweaver.consistency.domain.CharacterKnowledge;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterKnowledgeRepository extends JpaRepository<CharacterKnowledge, UUID> {
    Optional<CharacterKnowledge> findByProjectIdAndCharacterIdAndFactKey(
            UUID projectId, UUID characterId, String factKey);

    List<CharacterKnowledge> findAllByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    List<CharacterKnowledge> findAllByProjectIdAndCharacterIdOrderByUpdatedAtDesc(UUID projectId, UUID characterId);
}
