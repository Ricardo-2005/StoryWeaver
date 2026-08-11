package com.storyweaver.chapter.repository;

import com.storyweaver.chapter.domain.ChapterVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterVersionRepository extends JpaRepository<ChapterVersion, UUID> {
    List<ChapterVersion> findAllByChapterIdOrderByVersionNoDesc(UUID chapterId);

    Optional<ChapterVersion> findByChapterIdAndVersionNo(UUID chapterId, int versionNo);
}
