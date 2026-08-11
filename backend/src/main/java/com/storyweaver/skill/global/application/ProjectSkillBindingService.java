package com.storyweaver.skill.global.application;

import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.event.ProjectCreatedEvent;
import com.storyweaver.skill.global.domain.GlobalSkill;
import com.storyweaver.skill.global.domain.GlobalSkillVersion;
import com.storyweaver.skill.global.domain.ProjectSkillBinding;
import com.storyweaver.skill.global.domain.ProjectSkillBindingType;
import com.storyweaver.skill.global.repository.ProjectSkillBindingRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps a project on an immutable global Skill version; no silent version upgrades are allowed. */
@Service
public class ProjectSkillBindingService {
    private final ProjectSkillBindingRepository bindings;
    private final GlobalSkillService globalSkills;
    private final ProjectAccessService projectAccess;
    private final Clock clock;

    public ProjectSkillBindingService(
            ProjectSkillBindingRepository bindings,
            GlobalSkillService globalSkills,
            ProjectAccessService projectAccess,
            Clock clock) {
        this.bindings = bindings;
        this.globalSkills = globalSkills;
        this.projectAccess = projectAccess;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void bindProjectCreation(ProjectCreatedEvent event) {
        if (event.baseSkillVersionId() == null) {
            return;
        }
        GlobalSkillVersion version = globalSkills.requireBindableVersion(event.baseSkillVersionId(), event.ownerId());
        bindings.save(new ProjectSkillBinding(
                event.projectId(),
                version.getGlobalSkillId(),
                version.getId(),
                version.getSnapshotHash(),
                event.ownerId(),
                clock.instant()));
    }

    @Transactional
    public BindingView replaceFoundation(UUID projectId, UUID ownerId, UUID skillVersionId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        GlobalSkillVersion version = globalSkills.requireBindableVersion(skillVersionId, ownerId);
        bindings.findByProjectIdAndBindingType(projectId, ProjectSkillBindingType.FOUNDATION)
                .ifPresent(bindings::delete);
        ProjectSkillBinding binding = bindings.save(new ProjectSkillBinding(
                projectId,
                version.getGlobalSkillId(),
                version.getId(),
                version.getSnapshotHash(),
                ownerId,
                clock.instant()));
        return toView(binding, ownerId);
    }

    @Transactional(readOnly = true)
    public List<BindingView> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return bindings.findAllByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .map(binding -> toView(binding, ownerId))
                .toList();
    }

    @Transactional
    public void removeFoundation(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        bindings.findByProjectIdAndBindingType(projectId, ProjectSkillBindingType.FOUNDATION)
                .ifPresent(bindings::delete);
    }

    private BindingView toView(ProjectSkillBinding binding, UUID ownerId) {
        GlobalSkill skill = globalSkills.get(binding.getGlobalSkillId(), ownerId);
        return new BindingView(
                binding.getId(),
                binding.getProjectId(),
                binding.getBindingType(),
                binding.getGlobalSkillId(),
                binding.getGlobalSkillVersionId(),
                skill.getDisplayName(),
                binding.getSnapshotHash(),
                binding.isEnabled(),
                binding.getCreatedAt());
    }

    public record BindingView(
            UUID id,
            UUID projectId,
            ProjectSkillBindingType bindingType,
            UUID globalSkillId,
            UUID globalSkillVersionId,
            String skillName,
            String snapshotHash,
            boolean enabled,
            java.time.Instant createdAt) {}
}
