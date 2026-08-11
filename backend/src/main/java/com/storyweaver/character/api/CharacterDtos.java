package com.storyweaver.character.api;

import com.storyweaver.character.domain.LifeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CharacterDtos {
    private CharacterDtos() {}

    public record StateInput(
            LifeStatus lifeStatus,
            @Size(max = 200) String currentLocation,
            @Size(max = 5000) String physicalCondition,
            @Size(max = 5000) String emotionalState,
            @Size(max = 10000) String abilities,
            @Size(max = 10000) String inventoryNotes,
            @Size(max = 10000) String notes) {}

    public record CreateCharacterRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String aliases,
            @Size(max = 80) String role,
            @Size(max = 20000) String description,
            @Size(max = 20000) String personality,
            @Size(max = 20000) String background,
            @Size(max = 20000) String goals,
            @Size(max = 20000) String appearance,
            @Size(max = 20000) String notes,
            @Valid StateInput state) {}

    public record UpdateCharacterRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String aliases,
            @Size(max = 80) String role,
            @Size(max = 20000) String description,
            @Size(max = 20000) String personality,
            @Size(max = 20000) String background,
            @Size(max = 20000) String goals,
            @Size(max = 20000) String appearance,
            @Size(max = 20000) String notes,
            boolean archived,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record UpdateCharacterStateRequest(
            LifeStatus lifeStatus,
            @Size(max = 200) String currentLocation,
            @Size(max = 5000) String physicalCondition,
            @Size(max = 5000) String emotionalState,
            @Size(max = 10000) String abilities,
            @Size(max = 10000) String inventoryNotes,
            @Size(max = 10000) String notes,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record CharacterStateResponse(
            UUID id,
            UUID projectId,
            UUID characterId,
            LifeStatus lifeStatus,
            String currentLocation,
            String physicalCondition,
            String emotionalState,
            String abilities,
            String inventoryNotes,
            String notes,
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record CharacterResponse(
            UUID id,
            UUID projectId,
            String name,
            String aliases,
            String role,
            String description,
            String personality,
            String background,
            String goals,
            String appearance,
            String notes,
            boolean archived,
            long version,
            Instant createdAt,
            Instant updatedAt,
            CharacterStateResponse state) {}
}
