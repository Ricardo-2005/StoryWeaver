package com.storyweaver.skill.global.api;

import com.storyweaver.skill.global.api.GlobalSkillDtos.ConflictResolutionRequest;
import com.storyweaver.skill.global.api.GlobalSkillDtos.ContractValidationResponse;
import com.storyweaver.skill.global.api.GlobalSkillDtos.CreateForgeRunRequest;
import com.storyweaver.skill.global.api.GlobalSkillDtos.CreateGlobalSkillRequest;
import com.storyweaver.skill.global.api.GlobalSkillDtos.CreateGlobalSkillVersionRequest;
import com.storyweaver.skill.global.api.GlobalSkillDtos.ForgeRunResponse;
import com.storyweaver.skill.global.api.GlobalSkillDtos.GlobalSkillResponse;
import com.storyweaver.skill.global.api.GlobalSkillDtos.GlobalSkillVersionResponse;
import com.storyweaver.skill.global.api.GlobalSkillDtos.ManualTextSourceRequest;
import com.storyweaver.skill.global.api.GlobalSkillDtos.ResolveConflictsRequest;
import com.storyweaver.skill.global.api.GlobalSkillDtos.ReviewRuleRequest;
import com.storyweaver.skill.global.application.GlobalSkillService;
import com.storyweaver.skill.global.application.SkillArtifactService;
import com.storyweaver.skill.global.application.SkillForgeService;
import com.storyweaver.skill.global.domain.GlobalSkill;
import com.storyweaver.skill.global.domain.GlobalSkillVersion;
import com.storyweaver.skill.global.domain.SkillForgeRun;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Global, versioned Skill contracts. Project-local rules remain on SkillController. */
@RestController
@RequestMapping("/api")
public class GlobalSkillController {
    private final GlobalSkillService skills;
    private final SkillForgeService forge;
    private final SkillArtifactService artifacts;

    public GlobalSkillController(GlobalSkillService skills, SkillForgeService forge, SkillArtifactService artifacts) {
        this.skills = skills;
        this.forge = forge;
        this.artifacts = artifacts;
    }

    @GetMapping("/skills")
    List<GlobalSkillResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return skills.list(userId(jwt)).stream().map(this::skillResponse).toList();
    }

    @PostMapping("/skills")
    ResponseEntity<GlobalSkillResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateGlobalSkillRequest request) {
        GlobalSkill skill = skills.create(
                userId(jwt), request.slug(), request.displayName(), request.description(), request.contract());
        return ResponseEntity.created(URI.create("/api/skills/" + skill.getId()))
                .body(skillResponse(skill));
    }

    @GetMapping("/skills/{skillId}")
    GlobalSkillResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID skillId) {
        return skillResponse(skills.get(skillId, userId(jwt)));
    }

    @GetMapping("/skills/{skillId}/versions")
    List<GlobalSkillVersionResponse> versions(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID skillId) {
        return skills.versions(skillId, userId(jwt)).stream()
                .map(this::versionResponse)
                .toList();
    }

    @PostMapping("/skills/{skillId}/versions")
    ResponseEntity<GlobalSkillVersionResponse> createVersion(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID skillId,
            @Valid @RequestBody CreateGlobalSkillVersionRequest request) {
        GlobalSkillVersion version = skills.createVersion(skillId, userId(jwt), request.contract());
        return ResponseEntity.created(URI.create("/api/skills/" + skillId + "/versions/" + version.getId()))
                .body(versionResponse(version));
    }

    @PostMapping("/skills/{skillId}/validate")
    ContractValidationResponse validate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID skillId) {
        GlobalSkillService.ValidationResult result = skills.validate(skillId, userId(jwt));
        return new ContractValidationResponse(
                result.valid(),
                result.score(),
                result.missingSections(),
                result.version() == null ? null : versionResponse(result.version()));
    }

    @GetMapping("/skills/{skillId}/tests")
    List<SkillArtifactService.TestCaseView> tests(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID skillId) {
        return artifacts.tests(skillId, userId(jwt));
    }

    @GetMapping(value = "/skills/{skillId}/export", produces = "application/zip")
    ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID skillId) {
        SkillArtifactService.ExportArtifact artifact = artifacts.export(skillId, userId(jwt));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.filename() + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(artifact.content());
    }

    @DeleteMapping("/skills/{skillId}")
    ResponseEntity<Void> archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID skillId) {
        skills.archive(skillId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/skill-forge/runs")
    ResponseEntity<ForgeRunResponse> startForge(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateForgeRunRequest request) {
        SkillForgeRun run = forge.create(
                userId(jwt),
                request.slug(),
                request.displayName(),
                request.skillType(),
                request.materialTag(),
                request.genre(),
                request.sourceProjectId(),
                request.resolvedFocus(),
                request.materialDescription(),
                request.excludeCharacterNames(),
                request.excludeLocations(),
                request.excludePlotFacts(),
                request.reusableMethodsOnly(),
                request.ownershipConfirmed(),
                request.ownershipStatement());
        return ResponseEntity.created(URI.create("/api/skill-forge/runs/" + run.getId()))
                .body(forgeResponse(run));
    }

    @GetMapping("/skill-forge/runs/{runId}")
    ForgeRunResponse forgeRun(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return forgeResponse(forge.get(runId, userId(jwt)));
    }

    @GetMapping("/skill-forge/runs/{runId}/events")
    List<SkillForgeService.StepView> forgeEvents(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return forge.events(runId, userId(jwt));
    }

    @PostMapping("/skill-forge/runs/{runId}/sources/text")
    ResponseEntity<SkillForgeService.SourceView> addTextSource(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId,
            @Valid @RequestBody ManualTextSourceRequest request) {
        return ResponseEntity.created(URI.create("/api/skill-forge/runs/" + runId + "/sources"))
                .body(forge.addManualSource(
                        runId,
                        userId(jwt),
                        request.title(),
                        request.content(),
                        request.materialType(),
                        request.ownershipConfirmed()));
    }

    @PostMapping(value = "/skill-forge/runs/{runId}/sources/txt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<List<SkillForgeService.SourceView>> addTxtSources(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) List<String> titles,
            @RequestParam(defaultValue = "PROSE") String materialType,
            @RequestParam boolean ownershipConfirmed) {
        return ResponseEntity.created(URI.create("/api/skill-forge/runs/" + runId + "/sources"))
                .body(forge.addTxtSources(runId, userId(jwt), files, titles, materialType, ownershipConfirmed));
    }

    @GetMapping("/skill-forge/runs/{runId}/sources")
    List<SkillForgeService.SourceView> forgeSources(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return forge.sources(runId, userId(jwt));
    }

    @DeleteMapping("/skill-forge/runs/{runId}/sources/{sourceId}")
    ResponseEntity<Void> deleteForgeSource(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId, @PathVariable UUID sourceId) {
        forge.deleteSource(runId, sourceId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/skill-forge/runs/{runId}/start")
    ForgeRunResponse startDistillation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return forgeResponse(forge.start(runId, userId(jwt)));
    }

    @GetMapping("/skill-forge/runs/{runId}/rules")
    List<SkillForgeService.RuleView> forgeRules(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return forge.rules(runId, userId(jwt));
    }

    @PatchMapping("/skill-forge/runs/{runId}/rules/{ruleId}")
    SkillForgeService.RuleView reviewForgeRule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody ReviewRuleRequest request) {
        return forge.reviewRule(runId, ruleId, userId(jwt), request.action(), request.statement());
    }

    @PostMapping("/skill-forge/runs/{runId}/resolve-conflicts")
    List<SkillForgeService.RuleView> resolveForgeConflicts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID runId,
            @Valid @RequestBody ResolveConflictsRequest request) {
        return forge.resolveConflicts(
                runId,
                userId(jwt),
                request.resolutions().stream().map(this::conflictResolution).toList());
    }

    @PostMapping("/skill-forge/runs/{runId}/generate-contract")
    ForgeRunResponse generateForgeContract(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return forgeResponse(forge.generateContract(runId, userId(jwt)));
    }

    @PostMapping("/skill-forge/runs/{runId}/validate")
    ContractValidationResponse validateForge(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        SkillForgeService.ForgeValidation result = forge.validate(runId, userId(jwt));
        return new ContractValidationResponse(
                result.valid(),
                result.score(),
                result.missingSections(),
                result.version() == null ? null : versionResponse(result.version()));
    }

    @PostMapping("/skill-forge/runs/{runId}/cancel")
    ResponseEntity<Void> cancelForge(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        forge.cancel(runId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private GlobalSkillResponse skillResponse(GlobalSkill skill) {
        return new GlobalSkillResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getDescription(),
                skill.getScope(),
                skill.getStatus(),
                skill.getContract(),
                skill.getCurrentVersionId(),
                skill.getVersion(),
                skill.getCreatedAt(),
                skill.getUpdatedAt());
    }

    private GlobalSkillVersionResponse versionResponse(GlobalSkillVersion version) {
        return new GlobalSkillVersionResponse(
                version.getId(),
                version.getGlobalSkillId(),
                version.getVersionNo(),
                version.getContract(),
                version.getSnapshotHash(),
                version.getStatus(),
                version.getTokenEstimate(),
                version.getCreatedAt());
    }

    private ForgeRunResponse forgeResponse(SkillForgeRun run) {
        return new ForgeRunResponse(
                run.getId(),
                run.getGlobalSkillId(),
                run.getMode(),
                run.getStatus(),
                run.getSkillType(),
                run.getMaterialTag(),
                run.getGenre(),
                run.getSourceProjectId(),
                run.getLearningFocus(),
                run.getMaterialDescription(),
                run.isExcludeCharacterNames(),
                run.isExcludeLocations(),
                run.isExcludePlotFacts(),
                run.isReusableMethodsOnly(),
                run.getOwnershipConfirmedAt(),
                run.getCandidateContract(),
                run.getSummary(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }

    private SkillForgeService.ConflictResolution conflictResolution(ConflictResolutionRequest request) {
        return new SkillForgeService.ConflictResolution(request.ruleId(), request.action(), request.statement());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
