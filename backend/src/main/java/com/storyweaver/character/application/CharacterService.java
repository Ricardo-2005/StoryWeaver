package com.storyweaver.character.application;

import com.storyweaver.character.domain.Character;
import com.storyweaver.character.domain.CharacterImportance;
import com.storyweaver.character.domain.CharacterLifecycleStatus;
import com.storyweaver.character.domain.CharacterState;
import com.storyweaver.character.domain.LifeStatus;
import com.storyweaver.character.repository.CharacterRepository;
import com.storyweaver.character.repository.CharacterStateRepository;
import com.storyweaver.evolution.application.ProjectEvolutionService;
import com.storyweaver.evolution.application.ProjectEvolutionService.StateSnapshot;
import com.storyweaver.evolution.application.ProjectEvolutionService.TemporalStateView;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CharacterService {

    private final CharacterRepository characters;
    private final CharacterStateRepository states;
    private final ProjectAccessService projectAccess;
    private final ProjectEvolutionService evolution;
    private final Clock clock;

    public CharacterService(
            CharacterRepository characters,
            CharacterStateRepository states,
            ProjectAccessService projectAccess,
            ProjectEvolutionService evolution,
            Clock clock) {
        this.characters = characters;
        this.states = states;
        this.projectAccess = projectAccess;
        this.evolution = evolution;
        this.clock = clock;
    }

    @Transactional
    public CharacterDetails create(UUID projectId, UUID ownerId, CharacterValues values, StateValues stateValues) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        var now = clock.instant();
        Character character = new Character(projectId, values.name().trim(), now);
        character.update(
                values.name().trim(),
                nullable(values.aliases()),
                nullable(values.role()),
                nullable(values.description()),
                nullable(values.personality()),
                nullable(values.background()),
                nullable(values.goals()),
                nullable(values.appearance()),
                nullable(values.notes()),
                false,
                now);
        character.importance(values.importance() == null ? CharacterImportance.MINOR : values.importance(), now);
        characters.saveAndFlush(character);
        CharacterState state = new CharacterState(projectId, character.getId(), now);
        if (stateValues != null) {
            applyState(state, stateValues, now);
        }
        states.saveAndFlush(state);
        evolution.recordCharacterState(
                projectId, character.getId(), null, null, snapshot(state), "Character created", ownerId);
        return new CharacterDetails(character, state);
    }

    @Transactional(readOnly = true)
    public List<CharacterDetails> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return characters.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(this::details)
                .toList();
    }

    @Transactional(readOnly = true)
    public CharacterDetails get(UUID characterId, UUID ownerId) {
        return details(requireOwned(characterId, ownerId));
    }

    @Transactional
    public CharacterDetails update(
            UUID characterId, UUID ownerId, long expectedVersion, CharacterValues values, boolean archived) {
        Character character = requireOwned(characterId, ownerId);
        requireVersion(character.getVersion(), expectedVersion);
        character.update(
                values.name().trim(),
                nullable(values.aliases()),
                nullable(values.role()),
                nullable(values.description()),
                nullable(values.personality()),
                nullable(values.background()),
                nullable(values.goals()),
                nullable(values.appearance()),
                nullable(values.notes()),
                archived,
                clock.instant());
        character.importance(
                values.importance() == null ? character.getImportance() : values.importance(), clock.instant());
        characters.flush();
        evolution.invalidate(character.getProjectId(), "CHARACTER", character.getId(), "CHARACTER_PROFILE_CHANGED");
        return details(character);
    }

    @Transactional
    public CharacterState updateState(UUID characterId, UUID ownerId, long expectedVersion, StateValues values) {
        Character character = requireOwned(characterId, ownerId);
        CharacterState state = states.findByCharacterId(character.getId())
                .orElseThrow(() -> new IllegalStateException("Character state is missing"));
        requireVersion(state.getVersion(), expectedVersion);
        applyState(state, values, clock.instant());
        states.flush();
        evolution.recordCharacterState(
                character.getProjectId(), characterId, null, null, snapshot(state), "Manual state update", ownerId);
        return state;
    }

    @Transactional
    public CharacterDetails transition(
            UUID characterId, UUID ownerId, long expectedVersion, CharacterLifecycleStatus lifecycleStatus) {
        Character character = requireOwned(characterId, ownerId);
        requireVersion(character.getVersion(), expectedVersion);
        if (lifecycleStatus == CharacterLifecycleStatus.MERGED || lifecycleStatus == CharacterLifecycleStatus.PURGED) {
            throw new ConflictException(
                    "character_lifecycle_transition_invalid", "Use the explicit merge or permanent purge operation");
        }
        character.transition(lifecycleStatus, null, clock.instant());
        CharacterState state = states.findByCharacterId(characterId)
                .orElseThrow(() -> new IllegalStateException("Character state is missing"));
        if (lifecycleStatus == CharacterLifecycleStatus.DECEASED) {
            state.update(
                    LifeStatus.DEAD,
                    state.getCurrentLocation(),
                    state.getPhysicalCondition(),
                    state.getEmotionalState(),
                    state.getAbilities(),
                    state.getInventoryNotes(),
                    state.getNotes(),
                    clock.instant());
        }
        characters.flush();
        evolution.recordCharacterState(
                character.getProjectId(), characterId, null, null, snapshot(state), "Lifecycle transition", ownerId);
        return new CharacterDetails(character, state);
    }

    @Transactional
    public CharacterDetails merge(
            UUID sourceId, UUID targetId, UUID ownerId, long sourceExpectedVersion, long targetExpectedVersion) {
        if (sourceId.equals(targetId)) {
            throw new ConflictException("character_merge_invalid", "A character cannot be merged into itself");
        }
        Character source = requireOwned(sourceId, ownerId);
        Character target = requireOwned(targetId, ownerId);
        if (!source.getProjectId().equals(target.getProjectId())) {
            throw new NotFoundException("character_not_found", "Character was not found in this project");
        }
        requireVersion(source.getVersion(), sourceExpectedVersion);
        requireVersion(target.getVersion(), targetExpectedVersion);
        String aliases = mergeAliases(target.getAliases(), source.getName(), source.getAliases());
        target.update(
                target.getName(),
                aliases,
                target.getRole(),
                target.getDescription(),
                target.getPersonality(),
                target.getBackground(),
                target.getGoals(),
                target.getAppearance(),
                target.getNotes(),
                false,
                clock.instant());
        source.transition(CharacterLifecycleStatus.MERGED, target.getId(), clock.instant());
        characters.flush();
        evolution.invalidate(source.getProjectId(), "CHARACTER", source.getId(), "CHARACTER_MERGED");
        evolution.invalidate(target.getProjectId(), "CHARACTER", target.getId(), "CHARACTER_ALIAS_CHANGED");
        return details(source);
    }

    @Transactional
    public void purge(UUID characterId, UUID ownerId, long expectedVersion, String confirmation) {
        Character character = requireOwned(characterId, ownerId);
        requireVersion(character.getVersion(), expectedVersion);
        if (!"PURGE".equals(confirmation)) {
            throw new ConflictException(
                    "character_purge_confirmation_required", "Permanent purge requires the exact confirmation PURGE");
        }
        character.transition(CharacterLifecycleStatus.PURGED, null, clock.instant());
        characters.flush();
        evolution.prepareCharacterPurge(character.getProjectId(), character.getId());
        evolution.invalidate(character.getProjectId(), "CHARACTER", character.getId(), "CHARACTER_PURGED");
        characters.delete(character);
        characters.flush();
    }

    @Transactional(readOnly = true)
    public List<TemporalStateView> stateAt(UUID characterId, UUID ownerId, int chapterNo) {
        Character character = requireOwned(characterId, ownerId);
        return evolution.characterStateAt(character.getProjectId(), characterId, chapterNo);
    }

    private Character requireOwned(UUID characterId, UUID ownerId) {
        Character character = characters
                .findById(characterId)
                .orElseThrow(() -> new NotFoundException("character_not_found", "Character was not found"));
        projectAccess.requireOwnedProject(character.getProjectId(), ownerId);
        return character;
    }

    private CharacterDetails details(Character character) {
        CharacterState state = states.findByCharacterId(character.getId())
                .orElseThrow(() -> new IllegalStateException("Character state is missing"));
        return new CharacterDetails(character, state);
    }

    private void applyState(CharacterState state, StateValues values, java.time.Instant now) {
        state.update(
                values.lifeStatus() == null ? LifeStatus.UNKNOWN : values.lifeStatus(),
                nullable(values.currentLocation()),
                nullable(values.physicalCondition()),
                nullable(values.emotionalState()),
                nullable(values.abilities()),
                nullable(values.inventoryNotes()),
                nullable(values.notes()),
                now);
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new ConflictException("optimistic_lock_conflict", "The character changed; reload it before retrying");
        }
    }

    private String nullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String mergeAliases(String existing, String sourceName, String sourceAliases) {
        return java.util.stream.Stream.of(existing, sourceName, sourceAliases)
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> java.util.Arrays.stream(value.split("[,，]")))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private StateSnapshot snapshot(CharacterState state) {
        return new StateSnapshot(
                state.getLifeStatus().name(),
                state.getCurrentLocation(),
                state.getPhysicalCondition(),
                state.getEmotionalState(),
                state.getAbilities(),
                state.getInventoryNotes(),
                state.getNotes());
    }

    public record CharacterValues(
            String name,
            String aliases,
            String role,
            String description,
            String personality,
            String background,
            String goals,
            String appearance,
            String notes,
            CharacterImportance importance) {}

    public record StateValues(
            LifeStatus lifeStatus,
            String currentLocation,
            String physicalCondition,
            String emotionalState,
            String abilities,
            String inventoryNotes,
            String notes) {}

    public record CharacterDetails(Character character, CharacterState state) {}
}
