package com.storyweaver.canon.repository;

import com.storyweaver.canon.domain.CanonAsset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CanonAssetRepository extends JpaRepository<CanonAsset, UUID> {

    List<CanonAsset> findAllByProjectIdOrderByUpdatedAtDesc(UUID projectId);
}
