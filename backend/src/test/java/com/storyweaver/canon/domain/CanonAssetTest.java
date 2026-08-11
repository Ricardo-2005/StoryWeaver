package com.storyweaver.canon.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CanonAssetTest {

    @Test
    void revisingAConfirmedAssetCreatesANewDraftWithoutLosingTheConfirmedVersion() {
        Instant createdAt = Instant.parse("2026-08-02T00:00:00Z");
        CanonAsset asset = new CanonAsset(UUID.randomUUID(), "RULE", "Old rule", UUID.randomUUID(), createdAt);

        asset.confirm(createdAt.plusSeconds(1));
        int nextVersion = asset.revise("Revised rule", createdAt.plusSeconds(2));

        assertThat(nextVersion).isEqualTo(2);
        assertThat(asset.getCurrentVersionNo()).isEqualTo(2);
        assertThat(asset.getConfirmedVersionNo()).isEqualTo(1);
        assertThat(asset.getStatus()).isEqualTo(CanonStatus.DRAFT);
    }
}
