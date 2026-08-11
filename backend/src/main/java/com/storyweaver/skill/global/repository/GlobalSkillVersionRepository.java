package com.storyweaver.skill.global.repository;

import com.storyweaver.skill.global.domain.GlobalSkillVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalSkillVersionRepository extends JpaRepository<GlobalSkillVersion, UUID> {
    List<GlobalSkillVersion> findAllByGlobalSkillIdOrderByVersionNoDesc(UUID globalSkillId);

    Optional<GlobalSkillVersion> findTopByGlobalSkillIdOrderByVersionNoDesc(UUID globalSkillId);
}
