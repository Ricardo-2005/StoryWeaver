package com.storyweaver.skill.global.repository;

import com.storyweaver.skill.global.domain.SkillForgeRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillForgeRunRepository extends JpaRepository<SkillForgeRun, UUID> {
    Optional<SkillForgeRun> findByIdAndOwnerId(UUID id, UUID ownerId);
}
