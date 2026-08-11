package com.storyweaver.character.application;

import com.storyweaver.character.domain.Character;
import com.storyweaver.character.domain.CharacterState;
import com.storyweaver.character.domain.LifeStatus;
import com.storyweaver.character.repository.CharacterRepository;
import com.storyweaver.character.repository.CharacterStateRepository;
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
    private final Clock clock;

    public CharacterService(
            CharacterRepository characters,
            CharacterStateRepository states,
            ProjectAccessService projectAccess,
            Clock clock) {
        this.characters = characters;
        this.states = states;
        this.projectAccess = projectAccess;
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
        characters.save(character);
        CharacterState state = new CharacterState(projectId, character.getId(), now);
        if (stateValues != null) {
            applyState(state, stateValues, now);
        }
        states.save(state);
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
        characters.flush();
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
        return state;
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

    public record CharacterValues(
            String name,
            String aliases,
            String role,
            String description,
            String personality,
            String background,
            String goals,
            String appearance,
            String notes) {}

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
