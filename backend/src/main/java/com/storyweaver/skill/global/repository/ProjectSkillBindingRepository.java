package com.storyweaver.skill.global.repository;

import com.storyweaver.skill.global.domain.ProjectSkillBinding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSkillBindingRepository extends JpaRepository<ProjectSkillBinding, UUID> {
    List<ProjectSkillBinding> findAllByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<ProjectSkillBinding> findByProjectIdAndBindingType(
            UUID projectId, com.storyweaver.skill.global.domain.ProjectSkillBindingType bindingType);
}
