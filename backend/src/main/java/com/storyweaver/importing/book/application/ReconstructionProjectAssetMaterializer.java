package com.storyweaver.importing.book.application;

import com.storyweaver.production.application.RollingOutlineService;
import com.storyweaver.worldbook.application.WorldbookService;
import com.storyweaver.worldbook.application.WorldbookService.EntryValues;
import com.storyweaver.worldbook.domain.WorldbookEntry;
import com.storyweaver.worldbook.domain.WorldbookScope;
import com.storyweaver.worldbook.domain.WorldbookVisibility;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReconstructionProjectAssetMaterializer {
    static final String WORLDBOOK_TITLE = "TXT AI 重建 · 世界设定";
    private static final String WORLDBOOK_HEADER = "以下事实由 TXT AI 全项目重建根据导入原文整理：";
    private static final int WORLDBOOK_CONTENT_LIMIT = 190_000;
    private static final Pattern UNCERTAIN =
            Pattern.compile("NEEDS_REVIEW|冲突|不确定|可能|也许|似乎|疑似|无法确认|未确认|未经证实|不一定|推测|传闻|流传|匿名指控|不能直接证明");

    private final JdbcTemplate jdbc;
    private final WorldbookService worldbooks;
    private final RollingOutlineService rollingOutlines;
    private final Clock clock;

    public ReconstructionProjectAssetMaterializer(
            JdbcTemplate jdbc, WorldbookService worldbooks, RollingOutlineService rollingOutlines, Clock clock) {
        this.jdbc = jdbc;
        this.worldbooks = worldbooks;
        this.rollingOutlines = rollingOutlines;
        this.clock = clock;
    }

    @Transactional
    public MaterializationResult materialize(UUID jobId, UUID projectId, UUID ownerId) {
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtext(?))",
                (ResultSetExtractor<Void>) rs -> null,
                "reconstruction-project-assets:" + jobId);
        Integer completed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_reconstruction_step WHERE job_id=? AND step_name='PROJECT_ASSET_MATERIALIZATION'",
                Integer.class,
                jobId);
        if (completed != null && completed > 0) return new MaterializationResult(0, 0, false, 0, 0, 0);

        WorldbookResult worldbook = materializeWorldbook(jobId, projectId, ownerId);
        OutlineResult outline = materializeOutline(jobId, projectId, ownerId);
        ForeshadowResult foreshadows = materializeForeshadows(jobId, projectId);
        int processed = worldbook.appliedCandidates() + outline.appliedCandidates() + foreshadows.appliedCandidates();
        jdbc.update(
                """
                INSERT INTO book_reconstruction_step(
                    id,job_id,step_name,status,processed_units,total_units,summary,created_at)
                VALUES (?,?,'PROJECT_ASSET_MATERIALIZATION','COMPLETED',?,?,?,?)
                """,
                UUID.randomUUID(),
                jobId,
                processed,
                processed,
                "Materialized " + worldbook.entriesChanged() + " worldbook entries, "
                        + (outline.applied() ? 1 : 0) + " rolling outlines and "
                        + foreshadows.entriesCreated() + " foreshadow entries",
                Timestamp.from(clock.instant()));
        return new MaterializationResult(
                worldbook.entriesChanged(),
                worldbook.appliedCandidates(),
                outline.applied(),
                outline.appliedCandidates(),
                foreshadows.entriesCreated(),
                foreshadows.appliedCandidates());
    }

    private ForeshadowResult materializeForeshadows(UUID jobId, UUID projectId) {
        List<ForeshadowCandidate> candidates = jdbc.query(
                """
                SELECT id,chapter_id,content,confidence,evidence_count,source_anchors::text AS source_anchors
                FROM project_reconstruction_candidate
                WHERE job_id=? AND project_id=? AND candidate_type='FORESHADOW'
                  AND status IN ('CANDIDATE','ACCEPTED')
                  AND suggested_action='CREATE_FORESHADOW'
                ORDER BY created_at,id
                """,
                (rs, row) -> new ForeshadowCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("chapter_id", UUID.class),
                        rs.getString("content"),
                        rs.getString("confidence"),
                        rs.getInt("evidence_count"),
                        rs.getString("source_anchors")),
                jobId,
                projectId);
        if (candidates.isEmpty()) return new ForeshadowResult(0, 0);

        int created = 0;
        int applied = 0;
        Timestamp now = Timestamp.from(clock.instant());
        for (ForeshadowCandidate candidate : candidates) {
            UUID foreshadowId = UUID.randomUUID();
            int inserted = jdbc.update(
                    """
                    INSERT INTO foreshadow(
                        id,project_id,title,description,status,planted_chapter_id,target_chapter_no,
                        resolved_chapter_id,notes,version,created_at,updated_at,confidence,evidence,
                        retrieval_eligible,source_candidate_id)
                    VALUES (?,?,?,?, 'CANDIDATE',?,NULL,NULL,?,0,?,?,?,CAST(? AS jsonb),TRUE,?)
                    ON CONFLICT DO NOTHING
                    """,
                    foreshadowId,
                    projectId,
                    foreshadowTitle(candidate.content()),
                    candidate.content(),
                    candidate.chapterId(),
                    "TXT AI 拆书自动登记 · " + candidate.confidence() + " · Evidence " + candidate.evidenceCount(),
                    now,
                    now,
                    candidate.confidence(),
                    candidate.sourceAnchors(),
                    candidate.id());
            UUID targetId = inserted == 1
                    ? foreshadowId
                    : jdbc.query(
                            "SELECT id FROM foreshadow WHERE source_candidate_id=?",
                            rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                            candidate.id());
            if (targetId == null) continue;
            created += inserted;
            int updated = jdbc.update(
                    """
                    UPDATE project_reconstruction_candidate
                    SET target_entity_id=?,status='APPLIED',applied_at=COALESCE(applied_at,?),
                        retrieval_eligible=TRUE,revoked_at=NULL,revoked_by=NULL,revocation_reason=NULL,
                        policy_reason='Automatically registered in the foreshadow ledger; lifecycle remains unconfirmed',
                        updated_at=?
                    WHERE id=? AND job_id=? AND status IN ('CANDIDATE','ACCEPTED')
                    """,
                    targetId,
                    now,
                    now,
                    candidate.id(),
                    jobId);
            applied += updated;
        }
        return new ForeshadowResult(created, applied);
    }

    static String foreshadowTitle(String content) {
        if (content == null || content.isBlank()) return "AI 拆书伏笔";
        String title = content.strip().split("[，。；：\\r\\n]", 2)[0].strip();
        if (title.isBlank()) title = content.strip();
        return title.length() <= 160 ? title : title.substring(0, 160);
    }

    private WorldbookResult materializeWorldbook(UUID jobId, UUID projectId, UUID ownerId) {
        List<FactCandidate> candidates = jdbc.query(
                """
                SELECT id,content FROM project_reconstruction_candidate
                WHERE job_id=? AND candidate_type='WORLDBOOK'
                  AND status IN ('CANDIDATE','ACCEPTED') AND evidence_count>0
                  AND suggested_action='UPDATE_WORLD_ASSET'
                ORDER BY created_at,id
                """,
                (rs, row) -> new FactCandidate(rs.getObject("id", UUID.class), rs.getString("content")),
                jobId);
        List<FactCandidate> trusted = candidates.stream()
                .filter(candidate -> isTrustedWorldFact(candidate.content()))
                .toList();
        if (trusted.isEmpty()) return new WorldbookResult(0, 0);

        WorldbookEntry existing = worldbooks.list(projectId, ownerId).stream()
                .filter(entry -> WORLDBOOK_TITLE.equals(entry.getTitle()))
                .findFirst()
                .orElse(null);
        String content = existing == null || existing.getContent().isBlank()
                ? WORLDBOOK_HEADER
                : existing.getContent().strip();
        LinkedHashSet<String> existingFacts = new LinkedHashSet<>();
        Arrays.stream(content.split("\\R"))
                .map(String::strip)
                .filter(line -> line.startsWith("- "))
                .map(line -> line.substring(2).strip())
                .forEach(existingFacts::add);

        List<FactCandidate> selected = new ArrayList<>();
        StringBuilder merged = new StringBuilder(content);
        for (FactCandidate candidate : trusted) {
            String fact = candidate.content().strip();
            if (existingFacts.contains(fact)) {
                selected.add(candidate);
                continue;
            }
            String addition = "\n- " + fact;
            if (merged.length() + addition.length() > WORLDBOOK_CONTENT_LIMIT) break;
            merged.append(addition);
            existingFacts.add(fact);
            selected.add(candidate);
        }
        if (selected.isEmpty()) return new WorldbookResult(0, 0);

        WorldbookEntry target;
        if (existing == null) {
            target = worldbooks.create(
                    projectId,
                    ownerId,
                    new EntryValues(
                            WORLDBOOK_TITLE,
                            merged.toString(),
                            true,
                            false,
                            false,
                            List.of("TXT导入", "世界设定"),
                            500,
                            WorldbookScope.PROJECT,
                            null,
                            WorldbookVisibility.ALL,
                            null));
        } else if (!merged.toString().equals(existing.getContent())) {
            target = worldbooks.update(
                    existing.getId(),
                    ownerId,
                    existing.getVersion(),
                    new EntryValues(
                            existing.getTitle(),
                            merged.toString(),
                            existing.isActive(),
                            existing.isConstantEnabled(),
                            existing.isVectorEnabled(),
                            Arrays.asList(existing.getKeywords()),
                            existing.getPriority(),
                            existing.getScopeType(),
                            existing.getScopeRefId(),
                            existing.getVisibilityType(),
                            existing.getVisibilityRefId()));
        } else {
            target = existing;
        }
        applyCandidates(selected, jobId, target.getId());
        return new WorldbookResult(1, selected.size());
    }

    private OutlineResult materializeOutline(UUID jobId, UUID projectId, UUID ownerId) {
        OutlineCandidate candidate = jdbc.query(
                """
                SELECT id,content FROM project_reconstruction_candidate
                WHERE job_id=? AND candidate_type IN ('OUTLINE','PROJECT_OVERVIEW')
                  AND status IN ('CANDIDATE','ACCEPTED')
                  AND suggested_action='UPDATE_ROLLING_OUTLINE'
                ORDER BY CASE candidate_type WHEN 'OUTLINE' THEN 0 ELSE 1 END,created_at DESC
                LIMIT 1
                """,
                rs -> rs.next() ? new OutlineCandidate(rs.getObject("id", UUID.class), rs.getString("content")) : null,
                jobId);
        if (candidate == null || !isTrustedOutline(candidate.content())) return new OutlineResult(false, 0);

        List<ChapterRef> chapters = jdbc.query(
                "SELECT id,chapter_no FROM chapter WHERE project_id=? ORDER BY chapter_no,id",
                (rs, row) -> new ChapterRef(rs.getObject("id", UUID.class), rs.getInt("chapter_no")),
                projectId);
        if (chapters.isEmpty()) return new OutlineResult(false, 0);
        ChapterRef latest = chapters.getLast();
        boolean applied = rollingOutlines.applyReconstruction(
                projectId,
                ownerId,
                latest.chapterNo(),
                latest.id(),
                chapters.getFirst().chapterNo(),
                chapters.stream().map(ChapterRef::id).toList(),
                candidate.content().strip(),
                sha256(candidate.content().strip()));
        if (!applied) return new OutlineResult(false, 0);
        applyCandidates(List.of(new FactCandidate(candidate.id(), candidate.content())), jobId, projectId);
        return new OutlineResult(true, 1);
    }

    private void applyCandidates(List<FactCandidate> candidates, UUID jobId, UUID targetId) {
        Timestamp now = Timestamp.from(clock.instant());
        for (FactCandidate candidate : candidates) {
            jdbc.update(
                    """
                    UPDATE project_reconstruction_candidate
                    SET target_entity_id=?,status='APPLIED',applied_at=COALESCE(applied_at,?),
                        policy_reason='Automatically materialized after evidence and ambiguity gates',updated_at=?
                    WHERE id=? AND job_id=? AND status IN ('CANDIDATE','ACCEPTED')
                    """,
                    targetId,
                    now,
                    now,
                    candidate.id(),
                    jobId);
        }
    }

    static boolean isTrustedWorldFact(String content) {
        return content != null
                && !content.isBlank()
                && !UNCERTAIN.matcher(content).find();
    }

    static boolean isTrustedOutline(String content) {
        return content != null
                && !content.isBlank()
                && !content.contains("NEEDS_REVIEW")
                && !content.contains("冲突")
                && !content.contains("不确定");
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record FactCandidate(UUID id, String content) {}

    private record OutlineCandidate(UUID id, String content) {}

    private record ForeshadowCandidate(
            UUID id, UUID chapterId, String content, String confidence, int evidenceCount, String sourceAnchors) {}

    private record ChapterRef(UUID id, int chapterNo) {}

    private record WorldbookResult(int entriesChanged, int appliedCandidates) {}

    private record OutlineResult(boolean applied, int appliedCandidates) {}

    private record ForeshadowResult(int entriesCreated, int appliedCandidates) {}

    public record MaterializationResult(
            int worldbookEntries,
            int worldbookCandidates,
            boolean rollingOutline,
            int outlineCandidates,
            int foreshadowEntries,
            int foreshadowCandidates) {}
}
