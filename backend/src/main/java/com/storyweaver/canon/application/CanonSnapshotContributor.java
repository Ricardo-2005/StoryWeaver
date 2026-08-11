package com.storyweaver.canon.application;

import com.storyweaver.canon.repository.CanonAssetRepository;
import com.storyweaver.canon.repository.CanonAssetVersionRepository;
import com.storyweaver.project.application.ProjectSnapshotContributor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CanonSnapshotContributor implements ProjectSnapshotContributor {

    private final CanonAssetRepository assets;
    private final CanonAssetVersionRepository versions;

    public CanonSnapshotContributor(CanonAssetRepository assets, CanonAssetVersionRepository versions) {
        this.assets = assets;
        this.versions = versions;
    }

    @Override
    public String sectionName() {
        return "canonAssets";
    }

    @Override
    @Transactional(readOnly = true)
    public Object contribute(UUID projectId) {
        return assets.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(asset -> {
                    var version = versions.findByAssetIdAndVersionNo(asset.getId(), asset.getCurrentVersionNo())
                            .orElseThrow(() -> new IllegalStateException("Current canon asset version is missing"));
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("id", asset.getId());
                    data.put("assetType", asset.getAssetType());
                    data.put("name", asset.getName());
                    data.put("status", asset.getStatus().name());
                    data.put("currentVersionNo", asset.getCurrentVersionNo());
                    data.put("confirmedVersionNo", asset.getConfirmedVersionNo());
                    data.put("content", version.getContent());
                    return data;
                })
                .toList();
    }
}
