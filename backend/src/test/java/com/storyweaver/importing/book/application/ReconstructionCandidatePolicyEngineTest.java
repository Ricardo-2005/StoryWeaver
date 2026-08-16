package com.storyweaver.importing.book.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.character.domain.CharacterImportance;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconstructionCandidatePolicyEngineTest {
    private final ReconstructionCandidatePolicyEngine policy = new ReconstructionCandidatePolicyEngine(null);

    @Test
    void oneOffNamedMentionDoesNotCreateACharacterCard() {
        var result = decide("沈砚在山门前停了一步。", 1, List.of(), Map.of("沈砚", 1), new HashSet<>());

        assertThat(result.action()).isEqualTo(CandidateAction.APPEND_EVENT);
        assertThat(result.importance()).isEqualTo(CharacterImportance.MENTION_ONLY);
    }

    @Test
    void recurringNamedCharacterGetsOneCreateSuggestionOnly() {
        Set<String> assigned = new HashSet<>();
        Map<String, Integer> mentions = Map.of("沈砚", 3);

        var first = decide("沈砚在山门前停了一步。", 1, List.of(), mentions, assigned);
        var second = decide("沈砚受伤后回到药庐。", 1, List.of(), mentions, assigned);

        assertThat(first.action()).isEqualTo(CandidateAction.CREATE_CHARACTER);
        assertThat(second.action()).isEqualTo(CandidateAction.APPEND_STATE);
    }

    @Test
    void exactAliasResolvesToExistingCharacter() {
        UUID id = UUID.randomUUID();
        var existing = new ReconstructionCandidatePolicyEngine.KnownCharacter(id, "顾玄", "顾长老, 执法长老");

        var result = decide("顾长老发现灯芯仍有温度。", 1, List.of(existing), Map.of("顾长老", 2), new HashSet<>());

        assertThat(result.targetEntityId()).isEqualTo(id);
        assertThat(result.action()).isEqualTo(CandidateAction.APPEND_KNOWLEDGE);
    }

    @Test
    void sameNameDifferentPeopleRequiresReviewInsteadOfMerge() {
        var first = new ReconstructionCandidatePolicyEngine.KnownCharacter(UUID.randomUUID(), "周齐", null);
        var second = new ReconstructionCandidatePolicyEngine.KnownCharacter(UUID.randomUUID(), "周齐", "外门周齐");

        var result = decide("周齐确认法帖并非伪造。", 2, List.of(first, second), Map.of("周齐", 4), new HashSet<>());

        assertThat(result.action()).isEqualTo(CandidateAction.NEEDS_REVIEW);
        assertThat(result.targetEntityId()).isNull();
    }

    @Test
    void knowledgeAndRelationshipAreNotProfileOverwrites() {
        UUID id = UUID.randomUUID();
        var existing = new ReconstructionCandidatePolicyEngine.KnownCharacter(id, "许岚", null);

        var knowledge = decide("许岚得知青铜灯来自北荒。", 1, List.of(existing), Map.of("许岚", 5), new HashSet<>());
        var relationship = decide("许岚开始信任沈砚。", 1, List.of(existing), Map.of("许岚", 5), new HashSet<>());

        assertThat(knowledge.action()).isEqualTo(CandidateAction.APPEND_KNOWLEDGE);
        assertThat(relationship.action()).isEqualTo(CandidateAction.APPEND_RELATIONSHIP);
    }

    @Test
    void explicitEntityProfileCanCreateOneCanonicalCharacter() {
        Set<String> assigned = new HashSet<>();
        Map<String, Integer> mentions = Map.of("陆小棠", 2);

        var first = policy.decide(
                new ReconstructionCandidatePolicyEngine.PolicyCandidate(
                        UUID.randomUUID(), "ENTITY_RESOLUTION", "陆小棠是外门药庐记名弟子，修为炼气三层。", 1),
                List.of(),
                mentions,
                assigned);
        var second = policy.decide(
                new ReconstructionCandidatePolicyEngine.PolicyCandidate(
                        UUID.randomUUID(), "ENTITY_RESOLUTION", "陆小棠从失踪状态变为活着但被困。", 1),
                List.of(),
                mentions,
                assigned);

        assertThat(first.action()).isEqualTo(CandidateAction.CREATE_CHARACTER);
        assertThat(second.action()).isEqualTo(CandidateAction.NEEDS_REVIEW);
    }

    @Test
    void namedPlaceDoesNotBecomeACharacter() {
        var result = policy.decide(
                new ReconstructionCandidatePolicyEngine.PolicyCandidate(
                        UUID.randomUUID(), "ENTITY_RESOLUTION", "白石峰在内门北侧，是宗门禁地。", 1),
                List.of(),
                Map.of(),
                new HashSet<>());

        assertThat(result.action()).isEqualTo(CandidateAction.NEEDS_REVIEW);
        assertThat(result.subjectName()).isNull();
    }

    @Test
    void stripsTemporalPrefixAndStopsAtJudgementVerb() {
        var historical = policy.decide(
                new ReconstructionCandidatePolicyEngine.PolicyCandidate(
                        UUID.randomUUID(), "ENTITY_RESOLUTION", "十二年前顾玄都发现白石峰地下有一座旧洞府。", 1),
                List.of(),
                Map.of("顾玄都", 3),
                new HashSet<>());
        var judgement = policy.decide(
                new ReconstructionCandidatePolicyEngine.PolicyCandidate(
                        UUID.randomUUID(), "ENTITY_RESOLUTION", "许岚判断鞋印是女子鞋印。", 1),
                List.of(),
                Map.of("许岚", 3),
                new HashSet<>());

        assertThat(historical.subjectName()).isEqualTo("顾玄都");
        assertThat(judgement.subjectName()).isEqualTo("许岚");
    }

    @Test
    void singleExplicitProfileRemainsReviewOnly() {
        var result = policy.decide(
                new ReconstructionCandidatePolicyEngine.PolicyCandidate(
                        UUID.randomUUID(), "ENTITY_RESOLUTION", "叶青萝的修为是炼气八层。", 1),
                List.of(),
                Map.of("叶青萝", 1),
                new HashSet<>());

        assertThat(result.action()).isEqualTo(CandidateAction.NEEDS_REVIEW);
    }

    @Test
    void projectOverviewFeedsTheRollingOutline() {
        var result = policy.decide(
                new ReconstructionCandidatePolicyEngine.PolicyCandidate(
                        UUID.randomUUID(), "PROJECT_OVERVIEW", "全书仅根据已导入章节形成的阶段摘要", 0),
                List.of(),
                Map.of(),
                new HashSet<>());

        assertThat(result.action()).isEqualTo(CandidateAction.UPDATE_ROLLING_OUTLINE);
    }

    private ReconstructionCandidatePolicyEngine.PolicyDecision decide(
            String content,
            int evidence,
            List<ReconstructionCandidatePolicyEngine.KnownCharacter> characters,
            Map<String, Integer> mentions,
            Set<String> assigned) {
        Map<String, Integer> normalized = new HashMap<>();
        mentions.forEach((key, value) -> normalized.put(key.toLowerCase(), value));
        return policy.decide(
                new ReconstructionCandidatePolicyEngine.PolicyCandidate(
                        UUID.randomUUID(), "CHARACTER", content, evidence),
                characters,
                normalized,
                assigned);
    }
}
