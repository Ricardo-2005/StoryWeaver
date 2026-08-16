package com.storyweaver.importing.book.application;

import com.storyweaver.character.application.CharacterService;
import com.storyweaver.character.application.CharacterService.CharacterValues;
import com.storyweaver.character.application.CharacterService.StateValues;
import com.storyweaver.character.domain.CharacterImportance;
import com.storyweaver.character.domain.LifeStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconstructionCharacterMaterializer {
    private static final int DESCRIPTION_LIMIT = 20_000;

    private final JdbcTemplate jdbc;
    private final CharacterService characters;
    private final Clock clock;

    public ReconstructionCharacterMaterializer(JdbcTemplate jdbc, CharacterService characters, Clock clock) {
        this.jdbc = jdbc;
        this.characters = characters;
        this.clock = clock;
    }

    @Transactional
    public MaterializationResult materialize(UUID jobId, UUID projectId, UUID ownerId) {
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtext(?))",
                (ResultSetExtractor<Void>) rs -> null,
                "reconstruction-character:" + jobId);
        Integer recorded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_reconstruction_step WHERE job_id=? AND step_name='CHARACTER_MATERIALIZATION'",
                Integer.class,
                jobId);
        if (recorded != null && recorded > 0) return new MaterializationResult(0, 0, 0);
        List<CreateCandidate> createCandidates = jdbc.query(
                """
                SELECT id,subject_name,character_importance
                FROM project_reconstruction_candidate
                WHERE job_id=? AND suggested_action='CREATE_CHARACTER'
                  AND status IN ('CANDIDATE','ACCEPTED') AND retrieval_eligible=TRUE
                  AND subject_name IS NOT NULL
                ORDER BY evidence_count DESC,created_at,id
                """,
                (rs, row) -> new CreateCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("subject_name"),
                        rs.getString("character_importance")),
                jobId);
        int created = 0;
        int reused = 0;
        int applied = 0;
        for (CreateCandidate candidate : createCandidates) {
            UUID characterId = findExisting(projectId, candidate.subjectName());
            List<CandidateDetail> details = details(jobId, candidate.subjectName());
            if (characterId == null) {
                String description = description(details);
                int evidenceCount = details.stream()
                        .mapToInt(CandidateDetail::evidenceCount)
                        .sum();
                CharacterImportance importance = importance(candidate.importance());
                var result = characters.create(
                        projectId,
                        ownerId,
                        new CharacterValues(
                                candidate.subjectName().strip(),
                                null,
                                inferredRole(details, importance),
                                description,
                                null,
                                null,
                                null,
                                null,
                                "由 TXT AI 全项目重建自动创建；来源候选 " + details.size() + " 条，Evidence " + evidenceCount
                                        + "。请在人物页复核和补充结构化字段。",
                                importance),
                        new StateValues(
                                inferredLifeStatus(details),
                                null,
                                null,
                                null,
                                null,
                                null,
                                "当前状态尚未自动覆盖；原文变化已汇总到人物描述并保留 Candidate Evidence。"));
                characterId = result.character().getId();
                created++;
            } else {
                reused++;
            }
            applied += attachAndApply(jobId, candidate.subjectName(), characterId);
        }
        jdbc.update(
                """
                INSERT INTO book_reconstruction_step(
                    id,job_id,step_name,status,processed_units,total_units,summary,created_at)
                VALUES (?,?,'CHARACTER_MATERIALIZATION','COMPLETED',?,?,?,?)
                """,
                UUID.randomUUID(),
                jobId,
                applied,
                applied,
                "Automatically created " + created + " character cards",
                Timestamp.from(clock.instant()));
        return new MaterializationResult(created, reused, applied);
    }

    private List<CandidateDetail> details(UUID jobId, String subjectName) {
        return jdbc.query(
                """
                SELECT content,evidence_count
                FROM project_reconstruction_candidate
                WHERE job_id=? AND candidate_type IN ('CHARACTER','ENTITY_RESOLUTION')
                  AND lower(btrim(subject_name))=lower(btrim(?))
                  AND status NOT IN ('REJECTED','REVOKED','CONFLICT') AND retrieval_eligible=TRUE
                ORDER BY created_at,id
                """,
                (rs, row) -> new CandidateDetail(rs.getString("content"), rs.getInt("evidence_count")),
                jobId,
                subjectName);
    }

    private int attachAndApply(UUID jobId, String subjectName, UUID characterId) {
        return jdbc.update(
                """
                UPDATE project_reconstruction_candidate
                SET target_entity_id=?,status='APPLIED',applied_at=COALESCE(applied_at,?),
                    policy_reason=CASE WHEN policy_reason IS NULL OR policy_reason='' THEN
                        'Automatically materialized into the character module'
                    ELSE policy_reason || '; automatically materialized into the character module' END,
                    updated_at=?
                WHERE job_id=? AND candidate_type IN ('CHARACTER','ENTITY_RESOLUTION')
                  AND lower(btrim(subject_name))=lower(btrim(?))
                  AND status IN ('CANDIDATE','ACCEPTED') AND retrieval_eligible=TRUE
                """,
                characterId,
                Timestamp.from(clock.instant()),
                Timestamp.from(clock.instant()),
                jobId,
                subjectName);
    }

    private UUID findExisting(UUID projectId, String subjectName) {
        String wanted = normalize(subjectName);
        return jdbc.query(
                """
                SELECT id,name,aliases FROM character
                WHERE project_id=? AND lifecycle_status NOT IN ('MERGED','REJECTED','PURGED')
                ORDER BY created_at,id
                """,
                rs -> {
                    while (rs.next()) {
                        if (normalize(rs.getString("name")).equals(wanted)
                                || aliases(rs.getString("aliases")).contains(wanted)) {
                            return rs.getObject("id", UUID.class);
                        }
                    }
                    return null;
                },
                projectId);
    }

    private String description(List<CandidateDetail> details) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        details.stream()
                .map(CandidateDetail::content)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .forEach(distinct::add);
        String joined = String.join("\n", distinct);
        return joined.substring(0, Math.min(DESCRIPTION_LIMIT, joined.length()));
    }

    private String inferredRole(List<CandidateDetail> details, CharacterImportance importance) {
        String combined =
                details.stream().map(CandidateDetail::content).reduce("", (left, right) -> left + "\n" + right);
        if (combined.contains("主角") || combined.contains("主人公") || importance == CharacterImportance.PROTAGONIST) {
            return "主角";
        }
        if (combined.contains("反派")) return "反派";
        return null;
    }

    private LifeStatus inferredLifeStatus(List<CandidateDetail> details) {
        String latest = details.isEmpty() ? "" : details.getLast().content();
        if (latest != null && latest.matches(".*(?:死亡|死去|身亡|确认身死|已经死).*")) return LifeStatus.DEAD;
        return LifeStatus.UNKNOWN;
    }

    private CharacterImportance importance(String value) {
        if (value == null || value.isBlank()) return CharacterImportance.MINOR;
        return CharacterImportance.valueOf(value);
    }

    private Set<String> aliases(String value) {
        if (value == null || value.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value.split("[,，]"))
                .map(this::normalize)
                .filter(item -> !item.isBlank())
                .forEach(result::add);
        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private record CreateCandidate(UUID id, String subjectName, String importance) {}

    private record CandidateDetail(String content, int evidenceCount) {}

    public record MaterializationResult(int created, int reused, int appliedCandidates) {}
}
