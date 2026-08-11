package com.storyweaver.skill.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.skill.application.SkillComposer.BoundSkill;
import com.storyweaver.skill.domain.SkillBinding;
import com.storyweaver.skill.domain.SkillDefinition;
import com.storyweaver.skill.domain.SkillScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillComposerTest {

    private final SkillComposer composer = new SkillComposer();
    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID chapterId = UUID.randomUUID();

    @Test
    void composesByScopeAndReportsOnlySameScopeDisagreement() {
        BoundSkill base = skill("base", SkillScope.BASE, null, Map.of("PACING", "MEDIUM", "POV_MODE", "THIRD"));
        BoundSkill project = skill("project", SkillScope.PROJECT, null, Map.of("PACING", "FAST"));
        BoundSkill chapterA = skill("chapter-a", SkillScope.CHAPTER, chapterId, Map.of("PACING", "SLOW"));

        var resolved = composer.compose(List.of(chapterA, base, project));

        assertThat(resolved.resolved()).isTrue();
        assertThat(resolved.effectiveRules().get("PACING").value()).isEqualTo("SLOW");
        assertThat(resolved.effectiveRules().get("PACING").scope()).isEqualTo(SkillScope.CHAPTER);
        assertThat(resolved.effectiveRules().get("POV_MODE").value()).isEqualTo("THIRD");

        BoundSkill chapterB = skill("chapter-b", SkillScope.CHAPTER, chapterId, Map.of("PACING", "VERY_FAST"));
        var conflicted = composer.compose(List.of(base, project, chapterA, chapterB));

        assertThat(conflicted.resolved()).isFalse();
        assertThat(conflicted.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.scope()).isEqualTo(SkillScope.CHAPTER);
            assertThat(conflict.key()).isEqualTo("PACING");
            assertThat(conflict.values()).containsExactlyInAnyOrder("SLOW", "VERY_FAST");
        });
    }

    private BoundSkill skill(String name, SkillScope scope, UUID targetChapterId, Map<String, String> rules) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        SkillDefinition definition = new SkillDefinition(projectId, name, null, rules, true, userId, now);
        SkillBinding binding = new SkillBinding(projectId, definition.getId(), scope, targetChapterId, userId, now);
        return new BoundSkill(definition, binding);
    }
}
