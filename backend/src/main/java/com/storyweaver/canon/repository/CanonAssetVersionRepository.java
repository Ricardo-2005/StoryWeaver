package com.storyweaver.canon.repository;

import com.storyweaver.canon.domain.CanonAssetVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanonAssetVersionRepository extends JpaRepository<CanonAssetVersion, UUID> {

    Optional<CanonAssetVersion> findByAssetIdAndVersionNo(UUID assetId, int versionNo);
}
