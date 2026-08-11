package com.storyweaver.project.repository;

import com.storyweaver.project.domain.NovelProject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NovelProjectRepository extends JpaRepository<NovelProject, UUID> {

    Optional<NovelProject> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<NovelProject> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    List<NovelProject> findAllByOwnerIdAndArchivedFalseOrderByUpdatedAtDesc(UUID ownerId);
}
