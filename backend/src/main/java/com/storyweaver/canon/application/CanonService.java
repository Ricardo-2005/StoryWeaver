package com.storyweaver.canon.application;

import com.storyweaver.canon.domain.CanonAsset;
import com.storyweaver.canon.domain.CanonAssetVersion;
import com.storyweaver.canon.domain.CanonStatus;
import com.storyweaver.canon.repository.CanonAssetRepository;
import com.storyweaver.canon.repository.CanonAssetVersionRepository;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CanonService {

    private final CanonAssetRepository assets;
    private final CanonAssetVersionRepository versions;
    private final ProjectAccessService projectAccess;
    private final Clock clock;

    public CanonService(
            CanonAssetRepository assets,
            CanonAssetVersionRepository versions,
            ProjectAccessService projectAccess,
            Clock clock) {
        this.assets = assets;
        this.versions = versions;
        this.projectAccess = projectAccess;
        this.clock = clock;
    }

    @Transactional
    public AssetDetails create(
            UUID projectId, UUID ownerId, String assetType, String name, String content, String changeSummary) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        var now = clock.instant();
        CanonAsset asset = assets.save(new CanonAsset(projectId, normalizeType(assetType), name.trim(), ownerId, now));
        CanonAssetVersion version = versions.save(new CanonAssetVersion(
                projectId, asset.getId(), 1, asset.getName(), content, nullable(changeSummary), ownerId, now));
        return new AssetDetails(asset, version);
    }

    @Transactional(readOnly = true)
    public List<AssetDetails> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return assets.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(this::details)
                .toList();
    }

    @Transactional
    public AssetDetails update(
            UUID assetId, UUID ownerId, long expectedVersion, String name, String content, String changeSummary) {
        CanonAsset asset = requireOwnedAsset(assetId, ownerId);
        requireVersion(asset, expectedVersion);
        if (asset.getStatus() == CanonStatus.DEPRECATED) {
            throw new ConflictException("asset_deprecated", "A deprecated asset cannot be edited");
        }
        var now = clock.instant();
        int versionNo = asset.revise(name.trim(), now);
        CanonAssetVersion version = versions.save(new CanonAssetVersion(
                asset.getProjectId(),
                asset.getId(),
                versionNo,
                asset.getName(),
                content,
                nullable(changeSummary),
                ownerId,
                now));
        assets.flush();
        return new AssetDetails(asset, version);
    }

    @Transactional
    public AssetDetails confirm(UUID assetId, UUID ownerId, long expectedVersion) {
        CanonAsset asset = requireOwnedAsset(assetId, ownerId);
        requireVersion(asset, expectedVersion);
        if (asset.getStatus() == CanonStatus.DEPRECATED) {
            throw new ConflictException("asset_deprecated", "A deprecated asset cannot be confirmed");
        }
        asset.confirm(clock.instant());
        assets.flush();
        return details(asset);
    }

    @Transactional
    public AssetDetails deprecate(UUID assetId, UUID ownerId, long expectedVersion) {
        CanonAsset asset = requireOwnedAsset(assetId, ownerId);
        requireVersion(asset, expectedVersion);
        asset.deprecate(clock.instant());
        assets.flush();
        return details(asset);
    }

    private CanonAsset requireOwnedAsset(UUID assetId, UUID ownerId) {
        CanonAsset asset = assets.findById(assetId)
                .orElseThrow(() -> new NotFoundException("asset_not_found", "Canon asset was not found"));
        projectAccess.requireOwnedProject(asset.getProjectId(), ownerId);
        return asset;
    }

    private AssetDetails details(CanonAsset asset) {
        CanonAssetVersion version = versions.findByAssetIdAndVersionNo(asset.getId(), asset.getCurrentVersionNo())
                .orElseThrow(() -> new IllegalStateException("Current canon asset version is missing"));
        return new AssetDetails(asset, version);
    }

    private void requireVersion(CanonAsset asset, long expectedVersion) {
        if (asset.getVersion() != expectedVersion) {
            throw new ConflictException(
                    "optimistic_lock_conflict", "The canon asset changed; reload it before retrying");
        }
    }

    private String normalizeType(String assetType) {
        return assetType.trim().toUpperCase(Locale.ROOT);
    }

    private String nullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record AssetDetails(CanonAsset asset, CanonAssetVersion currentVersion) {}
}
