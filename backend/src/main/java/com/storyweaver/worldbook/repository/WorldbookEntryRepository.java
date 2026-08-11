package com.storyweaver.worldbook.repository;

import com.storyweaver.worldbook.domain.WorldbookEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldbookEntryRepository extends JpaRepository<WorldbookEntry, UUID> {
    List<WorldbookEntry> findAllByProjectIdOrderByPriorityDescTitleAsc(UUID projectId);
}
