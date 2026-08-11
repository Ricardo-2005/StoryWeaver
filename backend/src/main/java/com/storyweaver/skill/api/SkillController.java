package com.storyweaver.skill.api;

import com.storyweaver.skill.api.SkillDtos.ComposeSkillsRequest;
import com.storyweaver.skill.api.SkillDtos.CreateSkillRequest;
import com.storyweaver.skill.api.SkillDtos.EffectiveRuleResponse;
import com.storyweaver.skill.api.SkillDtos.SkillCompositionResponse;
import com.storyweaver.skill.api.SkillDtos.SkillConflictResponse;
import com.storyweaver.skill.api.SkillDtos.SkillResponse;
import com.storyweaver.skill.api.SkillDtos.UpdateSkillRequest;
import com.storyweaver.skill.application.SkillComposer.Composition;
import com.storyweaver.skill.application.SkillService;
import com.storyweaver.skill.application.SkillService.SkillDetails;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SkillController {
    private final SkillService service;

    public SkillController(SkillService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/skills")
    ResponseEntity<SkillResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSkillRequest request) {
        SkillDetails details = service.create(
                projectId,
                userId(jwt),
                request.name(),
                request.description(),
                request.rules(),
                request.enabled(),
                request.scope(),
                request.chapterId());
        return ResponseEntity.created(
                        URI.create("/api/skills/" + details.definition().getId()))
                .body(response(details));
    }

    @GetMapping("/projects/{projectId}/skills")
    List<SkillResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt)).stream().map(this::response).toList();
    }

    @PutMapping("/skills/{skillId}")
    SkillResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID skillId,
            @Valid @RequestBody UpdateSkillRequest request) {
        return response(service.update(
                skillId,
                userId(jwt),
                request.expectedVersion(),
                request.name(),
                request.description(),
                request.rules(),
                request.enabled(),
                request.scope(),
                request.chapterId()));
    }

    @PostMapping("/projects/{projectId}/skills/compose")
    SkillCompositionResponse compose(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody ComposeSkillsRequest request) {
        return composition(service.compose(projectId, userId(jwt), request.chapterId()));
    }

    private SkillResponse response(SkillDetails details) {
        var d = details.definition();
        var b = details.binding();
        return new SkillResponse(
                d.getId(),
                d.getProjectId(),
                d.getName(),
                d.getDescription(),
                d.getRules(),
                d.isEnabled(),
                b.getScope(),
                b.getChapterId(),
                d.getVersion(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }

    private SkillCompositionResponse composition(Composition composition) {
        var effective = new LinkedHashMap<String, EffectiveRuleResponse>();
        composition
                .effectiveRules()
                .forEach((key, rule) -> effective.put(
                        key,
                        new EffectiveRuleResponse(
                                rule.key(), rule.value(), rule.scope(), rule.skillId(), rule.skillName())));
        var conflicts = composition.conflicts().stream()
                .map(conflict -> new SkillConflictResponse(
                        conflict.scope(), conflict.key(), conflict.values(), conflict.skillIds()))
                .toList();
        return new SkillCompositionResponse(composition.resolved(), effective, conflicts);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
