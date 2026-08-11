package com.storyweaver.skill.global.api;

import com.storyweaver.skill.global.domain.ForgeRunStatus;
import com.storyweaver.skill.global.domain.GlobalSkillScope;
import com.storyweaver.skill.global.domain.GlobalSkillStatus;
import com.storyweaver.skill.global.domain.ProjectSkillBindingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GlobalSkillDtos {
    private GlobalSkillDtos() {}

    public record CreateGlobalSkillRequest(
            @NotBlank @Size(max = 80) String slug,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 1000) String description,
            @NotNull Map<String, Object> contract) {}

    public record CreateGlobalSkillVersionRequest(@NotNull Map<String, Object> contract) {}

    public record CreateForgeRunRequest(
            @NotBlank @Size(max = 80) String slug,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(max = 20) String skillType,
            @NotBlank @Size(max = 20) String materialTag,
            @Size(max = 80) String genre,
            UUID sourceProjectId,
            @Size(max = 1000) String focus,
            @Size(max = 1000) String learningFocus,
            @Size(max = 1000) String materialDescription,
            boolean excludeCharacterNames,
            boolean excludeLocations,
            boolean excludePlotFacts,
            boolean reusableMethodsOnly,
            boolean ownershipConfirmed,
            @Size(max = 2000) String ownershipStatement) {
        public String resolvedFocus() {
            return focus == null || focus.isBlank() ? learningFocus : focus;
        }
    }

    public record ManualTextSourceRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 50000) String content,
            @NotBlank @Size(max = 20) String materialType,
            boolean ownershipConfirmed) {}

    public record ReviewRuleRequest(@NotBlank @Size(max = 20) String action, @Size(max = 2000) String statement) {}

    public record ConflictResolutionRequest(
            @NotNull UUID ruleId, @NotBlank @Size(max = 20) String action, @Size(max = 2000) String statement) {}

    public record ResolveConflictsRequest(@NotNull List<@Valid ConflictResolutionRequest> resolutions) {}

    public record FoundationBindingRequest(@NotNull UUID globalSkillVersionId) {}

    public record GlobalSkillResponse(
            UUID id,
            String slug,
            String displayName,
            String description,
            GlobalSkillScope scope,
            GlobalSkillStatus status,
            Map<String, Object> contract,
            UUID currentVersionId,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record GlobalSkillVersionResponse(
            UUID id,
            UUID globalSkillId,
            int versionNo,
            Map<String, Object> contract,
            String snapshotHash,
            GlobalSkillStatus status,
            int tokenEstimate,
            Instant createdAt) {}

    public record ContractValidationResponse(
            boolean valid, int score, List<String> missingSections, GlobalSkillVersionResponse version) {}

    public record ForgeRunResponse(
            UUID id,
            UUID globalSkillId,
            String mode,
            ForgeRunStatus status,
            String skillType,
            String materialTag,
            String genre,
            UUID sourceProjectId,
            String learningFocus,
            String materialDescription,
            boolean excludeCharacterNames,
            boolean excludeLocations,
            boolean excludePlotFacts,
            boolean reusableMethodsOnly,
            Instant ownershipConfirmedAt,
            Map<String, Object> candidateContract,
            String summary,
            Instant createdAt,
            Instant updatedAt) {}

    public record FoundationBindingResponse(
            UUID id,
            UUID projectId,
            ProjectSkillBindingType bindingType,
            UUID globalSkillId,
            UUID globalSkillVersionId,
            String skillName,
            String snapshotHash,
            boolean enabled,
            Instant createdAt) {}
}
