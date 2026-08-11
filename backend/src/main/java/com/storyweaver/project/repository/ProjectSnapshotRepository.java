package com.storyweaver.project.repository;

import com.storyweaver.project.domain.ProjectSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectSnapshotRepository extends JpaRepository<ProjectSnapshot, UUID> {}
