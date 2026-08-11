package com.storyweaver.skill.application;

import com.storyweaver.chapter.domain.Chapter;
import com.storyweaver.chapter.repository.ChapterRepository;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.skill.application.SkillComposer.BoundSkill;
import com.storyweaver.skill.application.SkillComposer.Composition;
import com.storyweaver.skill.domain.SkillBinding;
import com.storyweaver.skill.domain.SkillDefinition;
import com.storyweaver.skill.domain.SkillRuleKey;
import com.storyweaver.skill.domain.SkillScope;
import com.storyweaver.skill.repository.SkillBindingRepository;
import com.storyweaver.skill.repository.SkillDefinitionRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {
    private final SkillDefinitionRepository definitions;
    private final SkillBindingRepository bindings;
    private final ChapterRepository chapters;
    private final ProjectAccessService projectAccess;
    private final SkillComposer composer;
    private final Clock clock;

    public SkillService(
            SkillDefinitionRepository definitions,
            SkillBindingRepository bindings,
            ChapterRepository chapters,
            ProjectAccessService projectAccess,
            SkillComposer composer,
            Clock clock) {
        this.definitions = definitions;
        this.bindings = bindings;
        this.chapters = chapters;
        this.projectAccess = projectAccess;
        this.composer = composer;
        this.clock = clock;
    }

    @Transactional
    public SkillDetails create(
            UUID projectId,
            UUID ownerId,
            String name,
            String description,
            Map<String, String> rules,
            boolean enabled,
            SkillScope scope,
            UUID chapterId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        validateBinding(projectId, scope, chapterId);
        var now = clock.instant();
        SkillDefinition definition = definitions.save(new SkillDefinition(
                projectId, name.trim(), nullable(description), normalizeRules(rules), enabled, ownerId, now));
        SkillBinding binding =
                bindings.save(new SkillBinding(projectId, definition.getId(), scope, chapterId, ownerId, now));
        return new SkillDetails(definition, binding);
    }

    @Transactional(readOnly = true)
    public List<SkillDetails> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        Map<UUID, SkillBinding> byDefinition = new TreeMap<>();
        bindings.findAllByProjectId(projectId)
                .forEach(binding -> byDefinition.put(binding.getSkillDefinitionId(), binding));
        return definitions.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(definition -> new SkillDetails(definition, requireBinding(byDefinition, definition.getId())))
                .toList();
    }

    @Transactional
    public SkillDetails update(
            UUID skillId,
            UUID ownerId,
            long expectedVersion,
            String name,
            String description,
            Map<String, String> rules,
            boolean enabled,
            SkillScope scope,
            UUID chapterId) {
        SkillDefinition definition = requireOwned(skillId, ownerId);
        requireVersion(definition.getVersion(), expectedVersion);
        validateBinding(definition.getProjectId(), scope, chapterId);
        SkillBinding binding = bindings.findBySkillDefinitionId(skillId)
                .orElseThrow(() -> new IllegalStateException("Skill binding is missing"));
        definition.update(name.trim(), nullable(description), normalizeRules(rules), enabled, clock.instant());
        binding.rebind(scope, chapterId);
        definitions.flush();
        return new SkillDetails(definition, binding);
    }

    @Transactional(readOnly = true)
    public Composition compose(UUID projectId, UUID ownerId, UUID chapterId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        if (chapterId != null) requireChapter(projectId, chapterId);
        Map<UUID, SkillDefinition> byId = new TreeMap<>();
        definitions
                .findAllByProjectIdOrderByUpdatedAtDesc(projectId)
                .forEach(definition -> byId.put(definition.getId(), definition));
        List<BoundSkill> selected = new ArrayList<>();
        for (SkillBinding binding : bindings.findAllByProjectId(projectId)) {
            if (binding.getScope() == SkillScope.CHAPTER
                    && !binding.getChapterId().equals(chapterId)) continue;
            SkillDefinition definition = byId.get(binding.getSkillDefinitionId());
            if (definition != null) selected.add(new BoundSkill(definition, binding));
        }
        return composer.compose(selected);
    }

    private SkillDefinition requireOwned(UUID skillId, UUID ownerId) {
        SkillDefinition definition = definitions
                .findById(skillId)
                .orElseThrow(() -> new NotFoundException("skill_not_found", "Skill was not found"));
        projectAccess.requireOwnedProject(definition.getProjectId(), ownerId);
        return definition;
    }

    private void validateBinding(UUID projectId, SkillScope scope, UUID chapterId) {
        if (scope == SkillScope.CHAPTER) {
            if (chapterId == null) {
                throw new BadRequestException("chapter_required", "A CHAPTER skill requires chapterId");
            }
            requireChapter(projectId, chapterId);
        } else if (chapterId != null) {
            throw new BadRequestException("chapter_not_allowed", "Only a CHAPTER skill may have chapterId");
        }
    }

    private Chapter requireChapter(UUID projectId, UUID chapterId) {
        Chapter chapter = chapters.findById(chapterId)
                .orElseThrow(() -> new NotFoundException("chapter_not_found", "Chapter was not found"));
        if (!chapter.getProjectId().equals(projectId)) {
            throw new NotFoundException("chapter_not_found", "Chapter was not found");
        }
        return chapter;
    }

    private Map<String, String> normalizeRules(Map<String, String> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new BadRequestException("skill_rules_required", "A skill requires at least one rule");
        }
        Map<String, String> normalized = new TreeMap<>();
        rules.forEach((key, value) -> {
            String normalizedKey = key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
            try {
                SkillRuleKey.valueOf(normalizedKey);
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("unsupported_skill_rule", "Unsupported Skill rule: " + normalizedKey);
            }
            String normalizedValue = value == null ? "" : value.trim();
            if (normalizedValue.isEmpty() || normalizedValue.length() > 2000) {
                throw new BadRequestException(
                        "invalid_skill_rule_value", "Skill rule values must contain 1-2000 characters");
            }
            normalized.put(normalizedKey, normalizedValue);
        });
        return normalized;
    }

    private SkillBinding requireBinding(Map<UUID, SkillBinding> bindingsByDefinition, UUID definitionId) {
        SkillBinding binding = bindingsByDefinition.get(definitionId);
        if (binding == null) throw new IllegalStateException("Skill binding is missing");
        return binding;
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new ConflictException("optimistic_lock_conflict", "The skill changed; reload it before retrying");
        }
    }

    private String nullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record SkillDetails(SkillDefinition definition, SkillBinding binding) {}
}
