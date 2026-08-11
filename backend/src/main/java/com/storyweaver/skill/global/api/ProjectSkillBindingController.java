package com.storyweaver.skill.global.api;

import com.storyweaver.skill.global.api.GlobalSkillDtos.FoundationBindingRequest;
import com.storyweaver.skill.global.api.GlobalSkillDtos.FoundationBindingResponse;
import com.storyweaver.skill.global.application.ProjectSkillBindingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/skill-bindings")
public class ProjectSkillBindingController {
    private final ProjectSkillBindingService bindings;

    public ProjectSkillBindingController(ProjectSkillBindingService bindings) {
        this.bindings = bindings;
    }

    @GetMapping
    List<FoundationBindingResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return bindings.list(projectId, userId(jwt)).stream()
                .map(this::response)
                .toList();
    }

    @PostMapping("/foundation")
    FoundationBindingResponse replace(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody FoundationBindingRequest request) {
        return response(bindings.replaceFoundation(projectId, userId(jwt), request.globalSkillVersionId()));
    }

    @DeleteMapping("/foundation")
    ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        bindings.removeFoundation(projectId, userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private FoundationBindingResponse response(ProjectSkillBindingService.BindingView binding) {
        return new FoundationBindingResponse(
                binding.id(),
                binding.projectId(),
                binding.bindingType(),
                binding.globalSkillId(),
                binding.globalSkillVersionId(),
                binding.skillName(),
                binding.snapshotHash(),
                binding.enabled(),
                binding.createdAt());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
