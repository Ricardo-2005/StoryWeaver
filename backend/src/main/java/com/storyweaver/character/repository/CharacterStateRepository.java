package com.storyweaver.character.repository;

import com.storyweaver.character.domain.CharacterState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterStateRepository extends JpaRepository<CharacterState, UUID> {
    Optional<CharacterState> findByCharacterId(UUID characterId);
}
