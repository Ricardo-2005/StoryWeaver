package com.storyweaver.canon.api;

import com.storyweaver.canon.domain.CanonStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CanonDtos {

    private CanonDtos() {}

    public record CreateAssetRequest(
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]*") String assetType,
            @NotBlank @Size(max = 120) String name,
            @NotNull @Size(max = 200000) String content,
            @Size(max = 500) String changeSummary) {}

    public record UpdateAssetRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Size(max = 200000) String content,
            @Size(max = 500) String changeSummary,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record AssetTransitionRequest(@NotNull @PositiveOrZero Long expectedVersion) {}

    public record AssetVersionResponse(
            UUID id, int versionNo, String name, String content, String changeSummary, Instant createdAt) {}

    public record AssetResponse(
            UUID id,
            UUID projectId,
            String assetType,
            String name,
            CanonStatus status,
            int currentVersionNo,
            Integer confirmedVersionNo,
            long version,
            Instant createdAt,
            Instant updatedAt,
            AssetVersionResponse currentVersion) {}
}
