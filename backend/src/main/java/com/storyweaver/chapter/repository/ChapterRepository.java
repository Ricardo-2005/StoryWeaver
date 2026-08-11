package com.storyweaver.chapter.repository;

import com.storyweaver.chapter.domain.Chapter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterRepository extends JpaRepository<Chapter, UUID> {
    List<Chapter> findAllByProjectIdOrderByChapterNoAsc(UUID projectId);
}
