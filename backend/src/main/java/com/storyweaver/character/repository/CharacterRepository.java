package com.storyweaver.character.repository;

import com.storyweaver.character.domain.Character;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterRepository extends JpaRepository<Character, UUID> {
    List<Character> findAllByProjectIdOrderByUpdatedAtDesc(UUID projectId);
}
