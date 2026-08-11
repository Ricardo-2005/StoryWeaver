package com.storyweaver.memory.repository;

import com.storyweaver.memory.domain.StoryEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryEventRepository extends JpaRepository<StoryEvent, UUID> {
    List<StoryEvent> findAllByProjectIdOrderByChapterNoDescCreatedAtDesc(UUID projectId);
}
