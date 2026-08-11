package com.storyweaver.worldbook.repository;

import com.storyweaver.worldbook.domain.Worldbook;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldbookRepository extends JpaRepository<Worldbook, UUID> {
    Optional<Worldbook> findByProjectId(UUID projectId);
}
