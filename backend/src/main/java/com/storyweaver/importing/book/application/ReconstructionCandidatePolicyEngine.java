package com.storyweaver.importing.book.application;

import com.storyweaver.character.domain.CharacterImportance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReconstructionCandidatePolicyEngine {
    private static final Pattern SUBJECT = Pattern.compile(
            "^([\\p{IsHan}]{2,8}?)(?=是|的|从|在|对|得知|知道|发现|意识|确认|认出|看到|听到|开始|变得|仍|将|收到|选择|与|修为|受伤|失踪|死亡|离开|来到|被|获得|没有|决定|判断|认为|表示|告诉|说道|说|回忆|察觉|推测)");
    private static final Pattern TEMPORAL_PREFIX =
            Pattern.compile("^(?:(?:[一二三四五六七八九十百千万两〇零0-9]+年前)|当年|此前|后来|随后|此时|当时|昨日|昨晚|今天|今夜|次日|翌日)[，,、：:\\s]*");
    private static final Pattern STATE = Pattern.compile("受伤|伤势|死亡|死去|失踪|离开|来到|位置|修为|能力|状态|恢复|醒来");
    private static final Pattern KNOWLEDGE = Pattern.compile("得知|知道|发现|意识|确认|认出|看到|听到|想起|不知|隐瞒");
    private static final Pattern RELATIONSHIP = Pattern.compile("信任|怀疑|敌视|合作|关系|喜欢|爱慕|背叛|结盟|师徒|同门");
    private static final Pattern PROFILE = Pattern.compile("身份|性格|外貌|目标|动机|所属|出身|背景");
    private static final Pattern EXPLICIT_PERSON_PROFILE = Pattern.compile(
            "是.{0,24}(?:主角|主人公|弟子|长老|师父|师母|师兄|师姐|师弟|师妹|掌门|执事|修士|凡人|少年|少女|男子|女子)|(?:身份|修为|年龄|年纪|任职|担任|出身)");
    private static final Pattern NON_PERSON_NAME = Pattern.compile(
            "^(?:主角|男主|女主|主人公|角色|此人|那人|众人)$|(?:山|峰|涧|谷|堂|阁|城|宗|门|派|宫|殿|楼|塔|剑|刀|灯|铃|术|法|诀|阵|符|丹|药|纸|袋|石|河|湖|海|州|国|界|域)$");

    private final JdbcTemplate jdbc;

    public ReconstructionCandidatePolicyEngine(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void classify(UUID jobId, UUID projectId) {
        List<PolicyCandidate> candidates = jdbc.query(
                "SELECT id,candidate_type,content,evidence_count FROM project_reconstruction_candidate WHERE job_id=? AND status IN ('CANDIDATE','CONFLICT') ORDER BY CASE status WHEN 'CANDIDATE' THEN 0 ELSE 1 END,evidence_count DESC,created_at,id",
                (rs, row) -> new PolicyCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("candidate_type"),
                        rs.getString("content"),
                        rs.getInt("evidence_count")),
                jobId);
        List<KnownCharacter> characters = jdbc.query(
                "SELECT id,name,aliases FROM character WHERE project_id=? AND lifecycle_status NOT IN ('MERGED','REJECTED','PURGED')",
                (rs, row) -> new KnownCharacter(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("aliases")),
                projectId);
        Map<String, Integer> mentions = new HashMap<>();
        for (PolicyCandidate candidate : candidates) {
            if ("CHARACTER".equals(candidate.type()) || "ENTITY_RESOLUTION".equals(candidate.type())) {
                subject(candidate.content()).ifPresent(name -> mentions.merge(normalize(name), 1, Integer::sum));
            }
        }
        Set<String> createAssigned = new HashSet<>();
        for (PolicyCandidate candidate : candidates) {
            PolicyDecision decision = decide(candidate, characters, mentions, createAssigned);
            jdbc.update(
                    "UPDATE project_reconstruction_candidate SET suggested_action=?,target_entity_id=?,subject_name=?,policy_reason=?,character_importance=?,updated_at=now() WHERE id=?",
                    decision.action().name(),
                    decision.targetEntityId(),
                    decision.subjectName(),
                    decision.reason(),
                    decision.importance() == null ? null : decision.importance().name(),
                    candidate.id());
        }
    }

    PolicyDecision decide(
            PolicyCandidate candidate,
            List<KnownCharacter> characters,
            Map<String, Integer> mentions,
            Set<String> createAssigned) {
        return switch (candidate.type()) {
            case "CHARACTER" -> characterDecision(candidate, characters, mentions, createAssigned);
            case "CHARACTER_KNOWLEDGE" ->
                genericCharacterDecision(candidate, characters, CandidateAction.APPEND_KNOWLEDGE);
            case "ENTITY_RESOLUTION" -> entityResolutionDecision(candidate, characters, mentions, createAssigned);
            case "EVENT" ->
                new PolicyDecision(
                        CandidateAction.APPEND_EVENT,
                        null,
                        subject(candidate.content()).orElse(null),
                        null,
                        "Historical event; structured current state must remain authoritative");
            case "WORLDBOOK" ->
                new PolicyDecision(
                        CandidateAction.UPDATE_WORLD_ASSET,
                        null,
                        null,
                        null,
                        "World fact requires entity resolution and versioned world state");
            case "FORESHADOW" ->
                new PolicyDecision(
                        CandidateAction.CREATE_FORESHADOW,
                        null,
                        null,
                        null,
                        "Foreshadow remains review-only until cross-chapter evidence is confirmed");
            case "OUTLINE", "PROJECT_OVERVIEW" ->
                new PolicyDecision(
                        CandidateAction.UPDATE_ROLLING_OUTLINE,
                        null,
                        null,
                        null,
                        "Reverse outline describes only imported text and must not invent future plot");
            default ->
                new PolicyDecision(
                        CandidateAction.NEEDS_REVIEW,
                        null,
                        null,
                        null,
                        "No deterministic lifecycle policy is available for this candidate type");
        };
    }

    private PolicyDecision characterDecision(
            PolicyCandidate candidate,
            List<KnownCharacter> characters,
            Map<String, Integer> mentions,
            Set<String> createAssigned) {
        String subject = subject(candidate.content()).orElse(null);
        if (subject == null) {
            return new PolicyDecision(
                    CandidateAction.APPEND_EVENT,
                    null,
                    null,
                    CharacterImportance.MENTION_ONLY,
                    "No stable named subject; keep as an event or mention instead of creating a card");
        }
        Resolution resolution = resolve(characters, subject);
        if (resolution.ambiguous()) {
            return new PolicyDecision(
                    CandidateAction.NEEDS_REVIEW,
                    null,
                    subject,
                    importance(mentions.getOrDefault(normalize(subject), 1)),
                    "Multiple existing characters share this name or alias; do not force-merge");
        }
        KnownCharacter existing = resolution.character();
        if (existing != null) {
            return new PolicyDecision(
                    contentAction(candidate.content()),
                    existing.id(),
                    subject,
                    importance(mentions.getOrDefault(normalize(subject), 1)),
                    "Resolved to an existing character by exact name or alias");
        }
        int count = mentions.getOrDefault(normalize(subject), 1);
        if ((count >= 2 || candidate.evidenceCount() >= 2) && createAssigned.add(normalize(subject))) {
            return new PolicyDecision(
                    CandidateAction.CREATE_CHARACTER,
                    null,
                    subject,
                    importance(count),
                    "Named subject recurs across independent candidates; propose one canonical card");
        }
        if (count >= 2) {
            return new PolicyDecision(
                    contentAction(candidate.content()),
                    null,
                    subject,
                    importance(count),
                    "Additional evidence belongs to the pending canonical character, not another card");
        }
        return new PolicyDecision(
                CandidateAction.APPEND_EVENT,
                null,
                subject,
                CharacterImportance.MENTION_ONLY,
                "Single mention does not meet the character-card creation gate");
    }

    private PolicyDecision genericCharacterDecision(
            PolicyCandidate candidate, List<KnownCharacter> characters, CandidateAction action) {
        String subject = subject(candidate.content()).orElse(null);
        Resolution resolution = subject == null ? Resolution.none() : resolve(characters, subject);
        if (resolution.ambiguous()) {
            return new PolicyDecision(
                    CandidateAction.NEEDS_REVIEW,
                    null,
                    subject,
                    null,
                    "Multiple existing characters share this name or alias; manual entity resolution is required");
        }
        KnownCharacter existing = resolution.character();
        CandidateAction resolvedAction =
                existing == null && action == CandidateAction.MERGE_ALIAS ? CandidateAction.NEEDS_REVIEW : action;
        return new PolicyDecision(
                resolvedAction,
                existing == null ? null : existing.id(),
                subject,
                null,
                existing == null
                        ? "Entity resolution is uncertain and cannot be force-merged"
                        : "Candidate resolved to an existing character");
    }

    private PolicyDecision entityResolutionDecision(
            PolicyCandidate candidate,
            List<KnownCharacter> characters,
            Map<String, Integer> mentions,
            Set<String> createAssigned) {
        String subject = subject(candidate.content()).orElse(null);
        if (subject == null) {
            return new PolicyDecision(
                    CandidateAction.NEEDS_REVIEW,
                    null,
                    null,
                    null,
                    "Entity result has no stable person subject and cannot create a character card");
        }
        Resolution resolution = resolve(characters, subject);
        if (resolution.ambiguous()) {
            return new PolicyDecision(
                    CandidateAction.NEEDS_REVIEW,
                    null,
                    subject,
                    null,
                    "Multiple existing characters share this name or alias; manual entity resolution is required");
        }
        if (resolution.character() != null) {
            return new PolicyDecision(
                    contentAction(candidate.content()),
                    resolution.character().id(),
                    subject,
                    importance(mentions.getOrDefault(normalize(subject), 1)),
                    "Resolved reconstruction detail to an existing character");
        }
        int count = mentions.getOrDefault(normalize(subject), 1);
        boolean explicitProfile =
                EXPLICIT_PERSON_PROFILE.matcher(candidate.content()).find();
        if (count >= 2 && createAssigned.add(normalize(subject))) {
            return new PolicyDecision(
                    CandidateAction.CREATE_CHARACTER,
                    null,
                    subject,
                    importance(count),
                    explicitProfile
                            ? "Explicit person profile can create one canonical character card"
                            : "Named person recurs across reconstruction evidence; create one canonical card");
        }
        return new PolicyDecision(
                CandidateAction.NEEDS_REVIEW,
                null,
                subject,
                CharacterImportance.MENTION_ONLY,
                "Single non-profile mention is not sufficient for automatic character creation");
    }

    private CandidateAction contentAction(String content) {
        if (KNOWLEDGE.matcher(content).find()) return CandidateAction.APPEND_KNOWLEDGE;
        if (RELATIONSHIP.matcher(content).find()) return CandidateAction.APPEND_RELATIONSHIP;
        if (STATE.matcher(content).find()) return CandidateAction.APPEND_STATE;
        if (PROFILE.matcher(content).find()) return CandidateAction.UPDATE_PROFILE;
        return CandidateAction.APPEND_EVENT;
    }

    private Resolution resolve(List<KnownCharacter> characters, String subject) {
        String wanted = normalize(subject);
        List<KnownCharacter> matches = new ArrayList<>();
        for (KnownCharacter character : characters) {
            boolean matched = normalize(character.name()).equals(wanted)
                    || character.aliases() != null
                            && Arrays.stream(character.aliases().split("[,，]"))
                                    .map(this::normalize)
                                    .anyMatch(wanted::equals);
            if (matched) matches.add(character);
        }
        if (matches.size() > 1) return new Resolution(null, true);
        return matches.isEmpty() ? Resolution.none() : new Resolution(matches.getFirst(), false);
    }

    private java.util.Optional<String> subject(String content) {
        if (content == null) return java.util.Optional.empty();
        String normalized = TEMPORAL_PREFIX.matcher(content.strip()).replaceFirst("");
        Matcher matcher = SUBJECT.matcher(normalized);
        if (!matcher.find()) return java.util.Optional.empty();
        String value = matcher.group(1);
        return NON_PERSON_NAME.matcher(value).find() ? java.util.Optional.empty() : java.util.Optional.of(value);
    }

    private CharacterImportance importance(int mentions) {
        if (mentions >= 12) return CharacterImportance.MAJOR;
        if (mentions >= 5) return CharacterImportance.SUPPORTING;
        if (mentions >= 2) return CharacterImportance.MINOR;
        return CharacterImportance.MENTION_ONLY;
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    record PolicyCandidate(UUID id, String type, String content, int evidenceCount) {}

    record KnownCharacter(UUID id, String name, String aliases) {}

    private record Resolution(KnownCharacter character, boolean ambiguous) {
        private static Resolution none() {
            return new Resolution(null, false);
        }
    }

    record PolicyDecision(
            CandidateAction action,
            UUID targetEntityId,
            String subjectName,
            CharacterImportance importance,
            String reason) {}
}
