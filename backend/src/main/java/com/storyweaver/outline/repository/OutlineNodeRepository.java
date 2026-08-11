package com.storyweaver.outline.repository;

import com.storyweaver.outline.domain.OutlineNode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutlineNodeRepository extends JpaRepository<OutlineNode, UUID> {
    List<OutlineNode> findAllByProjectIdOrderBySequenceNoAsc(UUID projectId);
}
