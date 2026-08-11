package com.storyweaver.skill.repository;

import com.storyweaver.skill.domain.SkillDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillDefinitionRepository extends JpaRepository<SkillDefinition, UUID> {
    List<SkillDefinition> findAllByProjectIdOrderByUpdatedAtDesc(UUID projectId);
}
