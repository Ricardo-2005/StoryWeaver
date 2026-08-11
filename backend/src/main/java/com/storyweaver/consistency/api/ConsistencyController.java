package com.storyweaver.consistency.api;

import com.storyweaver.consistency.api.ConsistencyDtos.CharacterKnowledgeResponse;
import com.storyweaver.consistency.api.ConsistencyDtos.FactResponse;
import com.storyweaver.consistency.api.ConsistencyDtos.ItemOwnershipResponse;
import com.storyweaver.consistency.application.ConsistencyQueryService;
import com.storyweaver.consistency.domain.FactStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConsistencyController {
    private final ConsistencyQueryService service;

    public ConsistencyController(ConsistencyQueryService service) {
        this.service = service;
    }

    @GetMapping("/projects/{projectId}/story-facts")
    List<FactResponse> facts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "ACCEPTED") FactStatus status) {
        return service.facts(projectId, userId(jwt), status);
    }

    @GetMapping("/projects/{projectId}/item-ownership")
    List<ItemOwnershipResponse> items(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.items(projectId, userId(jwt));
    }

    @GetMapping("/characters/{characterId}/knowledge")
    List<CharacterKnowledgeResponse> knowledge(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID characterId) {
        return service.knowledge(characterId, userId(jwt));
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
