package com.storyweaver.skill.api;

import com.storyweaver.skill.domain.SkillScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SkillDtos {
    private SkillDtos() {}

    public record CreateSkillRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotEmpty Map<@NotBlank String, @NotBlank String> rules,
            boolean enabled,
            @NotNull SkillScope scope,
            UUID chapterId) {}

    public record UpdateSkillRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotEmpty Map<@NotBlank String, @NotBlank String> rules,
            boolean enabled,
            @NotNull SkillScope scope,
            UUID chapterId,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record ComposeSkillsRequest(UUID chapterId) {}

    public record SkillResponse(
            UUID id,
            UUID projectId,
            String name,
            String description,
            Map<String, String> rules,
            boolean enabled,
            SkillScope scope,
            UUID chapterId,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record EffectiveRuleResponse(String key, String value, SkillScope scope, UUID skillId, String skillName) {}

    public record SkillConflictResponse(SkillScope scope, String key, List<String> values, List<UUID> skillIds) {}

    public record SkillCompositionResponse(
            boolean resolved,
            Map<String, EffectiveRuleResponse> effectiveRules,
            List<SkillConflictResponse> conflicts) {}
}
