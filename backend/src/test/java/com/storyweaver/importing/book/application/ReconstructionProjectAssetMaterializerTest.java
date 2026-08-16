package com.storyweaver.importing.book.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReconstructionProjectAssetMaterializerTest {
    @Test
    void acceptsExplicitWorldFacts() {
        assertThat(ReconstructionProjectAssetMaterializer.isTrustedWorldFact("白石峰是宗门禁地。"))
                .isTrue();
    }

    @Test
    void uncertainWorldFactsRemainCandidates() {
        assertThat(ReconstructionProjectAssetMaterializer.isTrustedWorldFact("白石峰可能藏有旧洞府。"))
                .isFalse();
        assertThat(ReconstructionProjectAssetMaterializer.isTrustedWorldFact("此事未经证实。"))
                .isFalse();
    }

    @Test
    void conflictedOutlineIsNotMaterialized() {
        assertThat(ReconstructionProjectAssetMaterializer.isTrustedOutline("NEEDS_REVIEW：章节结局不确定"))
                .isFalse();
        assertThat(ReconstructionProjectAssetMaterializer.isTrustedOutline("已导入章节的全书阶段摘要"))
                .isTrue();
    }

    @Test
    void derivesCompactForeshadowTitleFromCandidateContent() {
        assertThat(ReconstructionProjectAssetMaterializer.foreshadowTitle("沈砚在青崖山脚发现残灯，请求送往山巅古庙安息。"))
                .isEqualTo("沈砚在青崖山脚发现残灯");
        assertThat(ReconstructionProjectAssetMaterializer.foreshadowTitle(" ")).isEqualTo("AI 拆书伏笔");
    }
}
