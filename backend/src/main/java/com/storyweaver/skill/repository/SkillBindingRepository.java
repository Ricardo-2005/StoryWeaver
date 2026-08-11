package com.storyweaver.skill.repository;

import com.storyweaver.skill.domain.SkillBinding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillBindingRepository extends JpaRepository<SkillBinding, UUID> {
    Optional<SkillBinding> findBySkillDefinitionId(UUID skillDefinitionId);

    List<SkillBinding> findAllByProjectId(UUID projectId);
}
