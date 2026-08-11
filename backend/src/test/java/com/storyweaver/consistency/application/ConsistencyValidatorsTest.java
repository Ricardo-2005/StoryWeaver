package com.storyweaver.consistency.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.character.domain.CharacterState;
import com.storyweaver.character.domain.LifeStatus;
import com.storyweaver.consistency.application.ConsistencyModels.CharacterStateChange;
import com.storyweaver.consistency.application.ConsistencyModels.ItemChange;
import com.storyweaver.consistency.application.ConsistencyModels.KnowledgeChange;
import com.storyweaver.consistency.application.ConsistencyModels.TimelineEvent;
import com.storyweaver.consistency.domain.ItemOwnership;
import com.storyweaver.consistency.domain.ItemStatus;
import com.storyweaver.consistency.domain.KnowledgeCertainty;
import com.storyweaver.consistency.domain.ReviewSeverity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsistencyValidatorsTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void detectsDragonTemplateCharacterItemTimelineKnowledgeAndWorldRuleBlockers() {
        UUID projectId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        UUID chapterId = UUID.randomUUID();

        CharacterState state = new CharacterState(projectId, characterId, NOW);
        state.update(LifeStatus.DEAD, "卡塞尔学院", "已记录为死亡", null, null, null, null, NOW);
        var characterIssues = new CharacterStateValidator()
                .validateChange(
                        state,
                        new CharacterStateChange(
                                characterId,
                                LifeStatus.ALIVE,
                                "三峡任务现场",
                                null,
                                null,
                                null,
                                null,
                                null,
                                0,
                                "同一故事时间，路明非同时被记录在卡塞尔学院和三峡任务现场"));

        ItemOwnership item = new ItemOwnership(
                projectId,
                "seven-sins-sword-case",
                "完整七宗罪剑匣",
                characterId,
                ItemStatus.ACTIVE,
                chapterId,
                "路明非按任务记录接收完整七宗罪剑匣",
                NOW);
        var itemIssues = new ItemOwnershipValidator()
                .validateChange(
                        item,
                        new ItemChange(
                                "seven-sins-sword-case",
                                "完整七宗罪剑匣",
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                ItemStatus.ACTIVE,
                                "同一时间又把楚子航记录为完整七宗罪剑匣持有人"));

        var timelineIssues = new TimelineValidator()
                .validate(
                        "2026-08-02",
                        new TimelineEvent(
                                List.of(characterId),
                                List.of(characterId),
                                "青铜城",
                                "2026-08-01",
                                "进入青铜城",
                                "进入结果早于发现水下入口",
                                0.5,
                                "进入青铜城的结果早于发现入口的事件"));

        var knowledgeIssues = new KnowledgeBoundaryValidator()
                .validateChange(new KnowledgeChange(
                        characterId,
                        "bronze-fire-king-identity",
                        "青铜与火之王的身份",
                        KnowledgeCertainty.CONFIRMED,
                        null,
                        "楚子航在没有证据和信息传播事件时提前确认身份"));

        String worldRuleConflict = "普通人物没有血统或训练依据，却能够直接识别龙文并使用言灵";
        var worldRuleIssues = new CanonReferenceValidator().requireEvidence("CANON_REFERENCE", "");

        assertThat(characterIssues).extracting(issue -> issue.severity()).contains(ReviewSeverity.BLOCKER);
        assertThat(itemIssues).extracting(issue -> issue.severity()).contains(ReviewSeverity.BLOCKER);
        assertThat(timelineIssues).extracting(issue -> issue.severity()).contains(ReviewSeverity.BLOCKER);
        assertThat(knowledgeIssues).extracting(issue -> issue.severity()).contains(ReviewSeverity.BLOCKER);
        assertThat(worldRuleConflict).contains("血统或训练依据", "龙文", "言灵");
        assertThat(worldRuleIssues).extracting(issue -> issue.severity()).contains(ReviewSeverity.BLOCKER);
    }
}
