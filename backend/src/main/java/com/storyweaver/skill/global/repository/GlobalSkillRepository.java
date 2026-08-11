package com.storyweaver.skill.global.repository;

import com.storyweaver.skill.global.domain.GlobalSkill;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalSkillRepository extends JpaRepository<GlobalSkill, UUID> {
    List<GlobalSkill> findAllByScopeOrOwnerIdOrderByUpdatedAtDesc(
            com.storyweaver.skill.global.domain.GlobalSkillScope scope, UUID ownerId);

    Optional<GlobalSkill> findByIdAndOwnerId(UUID id, UUID ownerId);
}
