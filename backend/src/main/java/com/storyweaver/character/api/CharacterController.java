package com.storyweaver.character.api;

import com.storyweaver.character.api.CharacterDtos.*;
import com.storyweaver.character.application.CharacterService;
import com.storyweaver.character.application.CharacterService.CharacterDetails;
import com.storyweaver.character.application.CharacterService.CharacterValues;
import com.storyweaver.character.application.CharacterService.StateValues;
import com.storyweaver.character.domain.CharacterState;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CharacterController {
    private final CharacterService service;

    public CharacterController(CharacterService service) {
        this.service = service;
    }

    @PostMapping("/projects/{projectId}/characters")
    ResponseEntity<CharacterResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateCharacterRequest request) {
        CharacterDetails details =
                service.create(projectId, userId(jwt), values(request), stateValues(request.state()));
        return ResponseEntity.created(
                        URI.create("/api/characters/" + details.character().getId()))
                .body(response(details));
    }

    @GetMapping("/projects/{projectId}/characters")
    List<CharacterResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, userId(jwt)).stream().map(this::response).toList();
    }

    @GetMapping("/characters/{characterId}")
    CharacterResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID characterId) {
        return response(service.get(characterId, userId(jwt)));
    }

    @PutMapping("/characters/{characterId}")
    CharacterResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID characterId,
            @Valid @RequestBody UpdateCharacterRequest request) {
        return response(service.update(
                characterId, userId(jwt), request.expectedVersion(), values(request), request.archived()));
    }

    @GetMapping("/characters/{characterId}/state")
    CharacterStateResponse state(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID characterId) {
        return stateResponse(service.get(characterId, userId(jwt)).state());
    }

    @GetMapping("/characters/{characterId}/state-at")
    List<com.storyweaver.evolution.application.ProjectEvolutionService.TemporalStateView> stateAt(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID characterId, @RequestParam int chapterNo) {
        return service.stateAt(characterId, userId(jwt), chapterNo);
    }

    @PutMapping("/characters/{characterId}/state")
    CharacterStateResponse updateState(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID characterId,
            @Valid @RequestBody UpdateCharacterStateRequest request) {
        StateValues values = new StateValues(
                request.lifeStatus(),
                request.currentLocation(),
                request.physicalCondition(),
                request.emotionalState(),
                request.abilities(),
                request.inventoryNotes(),
                request.notes());
        return stateResponse(service.updateState(characterId, userId(jwt), request.expectedVersion(), values));
    }

    @PostMapping("/characters/{characterId}/lifecycle")
    CharacterResponse transition(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID characterId,
            @Valid @RequestBody CharacterLifecycleRequest request) {
        return response(service.transition(characterId, userId(jwt), request.expectedVersion(), request.status()));
    }

    @PostMapping("/characters/{characterId}/merge")
    CharacterResponse merge(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID characterId,
            @Valid @RequestBody MergeCharacterRequest request) {
        return response(service.merge(
                characterId,
                request.targetCharacterId(),
                userId(jwt),
                request.sourceExpectedVersion(),
                request.targetExpectedVersion()));
    }

    @PostMapping("/characters/{characterId}/purge")
    ResponseEntity<Void> purge(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID characterId,
            @Valid @RequestBody PurgeCharacterRequest request) {
        service.purge(characterId, userId(jwt), request.expectedVersion(), request.confirmation());
        return ResponseEntity.noContent().build();
    }

    private CharacterValues values(CreateCharacterRequest r) {
        return new CharacterValues(
                r.name(),
                r.aliases(),
                r.role(),
                r.description(),
                r.personality(),
                r.background(),
                r.goals(),
                r.appearance(),
                r.notes(),
                r.importance());
    }

    private CharacterValues values(UpdateCharacterRequest r) {
        return new CharacterValues(
                r.name(),
                r.aliases(),
                r.role(),
                r.description(),
                r.personality(),
                r.background(),
                r.goals(),
                r.appearance(),
                r.notes(),
                r.importance());
    }

    private StateValues stateValues(StateInput r) {
        return r == null
                ? null
                : new StateValues(
                        r.lifeStatus(),
                        r.currentLocation(),
                        r.physicalCondition(),
                        r.emotionalState(),
                        r.abilities(),
                        r.inventoryNotes(),
                        r.notes());
    }

    private CharacterResponse response(CharacterDetails details) {
        var c = details.character();
        return new CharacterResponse(
                c.getId(),
                c.getProjectId(),
                c.getName(),
                c.getAliases(),
                c.getRole(),
                c.getDescription(),
                c.getPersonality(),
                c.getBackground(),
                c.getGoals(),
                c.getAppearance(),
                c.getNotes(),
                c.isArchived(),
                c.getImportance(),
                c.getLifecycleStatus(),
                c.getMergedInto(),
                c.isRetrievalEligible(),
                c.getVersion(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                stateResponse(details.state()));
    }

    private CharacterStateResponse stateResponse(CharacterState s) {
        return new CharacterStateResponse(
                s.getId(),
                s.getProjectId(),
                s.getCharacterId(),
                s.getLifeStatus(),
                s.getCurrentLocation(),
                s.getPhysicalCondition(),
                s.getEmotionalState(),
                s.getAbilities(),
                s.getInventoryNotes(),
                s.getNotes(),
                s.getVersion(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
