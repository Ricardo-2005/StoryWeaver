package com.storyweaver.project.application;

import com.storyweaver.project.domain.NovelProject;
import com.storyweaver.project.repository.NovelProjectRepository;
import com.storyweaver.shared.error.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAccessService {

    private final NovelProjectRepository projects;

    public ProjectAccessService(NovelProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public OwnedProject requireOwnedProject(UUID projectId, UUID ownerId) {
        NovelProject project = projects.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> new NotFoundException("project_not_found", "Project was not found"));
        return new OwnedProject(project.getId(), project.getVersion(), project.isArchived());
    }

    public record OwnedProject(UUID id, long version, boolean archived) {}
}
