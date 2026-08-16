package com.storyweaver.evolution.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProjectEvolutionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public ProjectEvolutionService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public void recordCharacterState(
            UUID projectId,
            UUID characterId,
            UUID sourceChapterId,
            Integer sourceChapterNo,
            StateSnapshot state,
            String evidence,
            UUID userId) {
        int chapterNo = sourceChapterNo == null ? currentChapter(projectId) : Math.max(1, sourceChapterNo);
        UUID nextId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO character_state_timeline(
                    id,project_id,character_id,life_status,current_location,physical_condition,
                    emotional_state,abilities,inventory_notes,notes,valid_from_chapter_no,
                    source_chapter_id,evidence,confidence,status,created_by,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'HIGH','ACTIVE',?,?)
                """,
                nextId,
                projectId,
                characterId,
                state.lifeStatus(),
                state.currentLocation(),
                state.physicalCondition(),
                state.emotionalState(),
                state.abilities(),
                state.inventoryNotes(),
                state.notes(),
                chapterNo,
                sourceChapterId,
                evidence,
                userId,
                timestamp());
        jdbc.update(
                """
                UPDATE character_state_timeline
                SET valid_to_chapter_no=?,status='SUPERSEDED',superseded_by=?
                WHERE project_id=? AND character_id=? AND id<>?
                  AND valid_to_chapter_no IS NULL AND status='ACTIVE'
                """,
                chapterNo,
                nextId,
                projectId,
                characterId,
                nextId);
        invalidate(projectId, "CHARACTER", characterId, "CHARACTER_STATE_CHANGED");
    }

    @Transactional
    public void recordItem(UUID projectId, UUID chapterId, int chapterNo, ItemSnapshot item) {
        UUID nextId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO item_ownership_timeline(
                    id,project_id,item_key,item_name,owner_character_id,item_status,
                    valid_from_chapter_no,source_chapter_id,evidence,status,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,'ACTIVE',?)
                """,
                nextId,
                projectId,
                item.itemKey(),
                item.itemName(),
                item.ownerCharacterId(),
                item.itemStatus(),
                chapterNo,
                chapterId,
                item.evidence(),
                timestamp());
        jdbc.update(
                """
                UPDATE item_ownership_timeline
                SET valid_to_chapter_no=?,status='SUPERSEDED',superseded_by=?
                WHERE project_id=? AND item_key=? AND id<>?
                  AND valid_to_chapter_no IS NULL AND status='ACTIVE'
                """,
                chapterNo,
                nextId,
                projectId,
                item.itemKey(),
                nextId);
    }

    @Transactional
    public void confirmedChapterCommitted(
            UUID projectId, UUID chapterId, int chapterNo, String chapterSummary, UUID userId) {
        jdbc.update(
                """
                UPDATE story_fact SET valid_from_chapter_no=?,retrieval_eligible=(status='ACCEPTED'),
                    lifecycle_status=CASE WHEN status='REJECTED' THEN 'REJECTED' ELSE 'ACTIVE' END,
                    content_hash=encode(digest(content,'sha256'),'hex')
                WHERE chapter_id=?
                """,
                chapterNo,
                chapterId);
        refreshRollingOutline(projectId, chapterId, chapterNo, chapterSummary);
        invalidateContextPackets(projectId);
        jdbc.update(
                "INSERT INTO asset_invalidation(id,project_id,asset_type,asset_id,reason,status,created_at) VALUES (?,?,?,?,?,'REFRESHED',?)",
                UUID.randomUUID(),
                projectId,
                "ROLLING_OUTLINE",
                projectId,
                "CONFIRMED_CHAPTER_COMMIT",
                timestamp());
    }

    @Transactional
    public void invalidate(UUID projectId, String assetType, UUID assetId, String reason) {
        Timestamp now = timestamp();
        jdbc.update(
                "INSERT INTO asset_invalidation(id,project_id,asset_type,asset_id,reason,status,created_at) VALUES (?,?,?,?,?,'STALE',?)",
                UUID.randomUUID(),
                projectId,
                assetType,
                assetId,
                reason,
                now);
        if ("CHAPTER".equals(assetType)) {
            jdbc.update(
                    "UPDATE chapter_reconstruction_metadata SET lifecycle_status='STALE',updated_at=? WHERE chapter_id=?",
                    now,
                    assetId);
            jdbc.update(
                    "UPDATE story_event SET retrieval_eligible=FALSE,lifecycle_status='SUPERSEDED',embedding=NULL,embedding_status='NOT_REQUESTED' WHERE chapter_id=?",
                    assetId);
        }
        jdbc.update("UPDATE rolling_outline SET stale=TRUE,updated_at=? WHERE project_id=?", now, projectId);
        invalidateContextPackets(projectId);
    }

    public List<TemporalStateView> characterStateAt(UUID projectId, UUID characterId, int chapterNo) {
        return jdbc.query(
                """
                SELECT valid_from_chapter_no,valid_to_chapter_no,life_status,current_location,
                    physical_condition,emotional_state,evidence,status
                FROM character_state_timeline
                WHERE project_id=? AND character_id=? AND valid_from_chapter_no<=?
                  AND (valid_to_chapter_no IS NULL OR valid_to_chapter_no>?)
                  AND status<>'REVOKED'
                ORDER BY valid_from_chapter_no DESC,created_at DESC
                """,
                (rs, row) -> new TemporalStateView(
                        rs.getInt("valid_from_chapter_no"),
                        (Integer) rs.getObject("valid_to_chapter_no"),
                        rs.getString("life_status"),
                        rs.getString("current_location"),
                        rs.getString("physical_condition"),
                        rs.getString("emotional_state"),
                        rs.getString("evidence"),
                        rs.getString("status")),
                projectId,
                characterId,
                chapterNo,
                chapterNo);
    }

    @Transactional
    public void prepareCharacterPurge(UUID projectId, UUID characterId) {
        jdbc.update(
                "UPDATE story_event SET participant_ids=array_remove(participant_ids,?),known_by_ids=array_remove(known_by_ids,?) WHERE project_id=?",
                characterId,
                characterId,
                projectId);
        jdbc.update(
                "UPDATE foreshadow SET related_character_ids=array_remove(related_character_ids,?) WHERE project_id=?",
                characterId,
                projectId);
        jdbc.update(
                "UPDATE worldbook_entry SET scope_type='PROJECT',scope_ref_id=NULL WHERE project_id=? AND scope_type='CHARACTER' AND scope_ref_id=?",
                projectId,
                characterId);
        jdbc.update(
                "UPDATE worldbook_entry SET visibility_type='AUTHOR_ONLY',visibility_ref_id=NULL WHERE project_id=? AND visibility_type='CHARACTER_ONLY' AND visibility_ref_id=?",
                projectId,
                characterId);
        jdbc.update(
                "UPDATE project_reconstruction_candidate SET target_entity_id=NULL,suggested_action='NEEDS_REVIEW',policy_reason='Target character was purged',retrieval_eligible=FALSE,updated_at=? WHERE project_id=? AND target_entity_id=?",
                timestamp(),
                projectId,
                characterId);
        jdbc.update(
                "UPDATE character SET lifecycle_status='ARCHIVED',archived=TRUE,retrieval_eligible=FALSE,merged_into=NULL,updated_at=? WHERE project_id=? AND merged_into=?",
                timestamp(),
                projectId,
                characterId);
        invalidateContextPackets(projectId);
    }

    private void refreshRollingOutline(UUID projectId, UUID chapterId, int chapterNo, String currentSummary) {
        Integer configuredWindow = jdbc.query(
                "SELECT window_size FROM rolling_outline WHERE project_id=?",
                rs -> rs.next() ? rs.getInt(1) : null,
                projectId);
        int window = configuredWindow == null ? 5 : configuredWindow;
        int from = Math.max(1, chapterNo - window + 1);
        List<ChapterSnapshot> chapters = jdbc.query(
                """
                SELECT c.id,c.chapter_no,c.title,cv.summary
                FROM chapter c
                LEFT JOIN chapter_version cv ON cv.chapter_id=c.id AND cv.version_no=c.current_version_no
                WHERE c.project_id=? AND c.status='CONFIRMED' AND c.chapter_no BETWEEN ? AND ?
                ORDER BY c.chapter_no
                """,
                (rs, row) -> new ChapterSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getInt("chapter_no"),
                        rs.getString("title"),
                        rs.getString("summary")),
                projectId,
                from,
                chapterNo);
        if (chapters.stream().noneMatch(value -> value.id().equals(chapterId))) {
            chapters = new java.util.ArrayList<>(chapters);
            chapters.add(new ChapterSnapshot(chapterId, chapterNo, "", currentSummary));
            chapters.sort(java.util.Comparator.comparingInt(ChapterSnapshot::chapterNo));
        }
        String recentSummary = chapters.stream()
                .map(value -> "第" + value.chapterNo() + "章 " + value.title() + "："
                        + (value.summary() == null || value.summary().isBlank() ? "（无摘要）" : value.summary()))
                .collect(java.util.stream.Collectors.joining("\n"));
        List<String> locations = jdbc.queryForList(
                """
                SELECT DISTINCT cs.current_location FROM character_state cs
                JOIN character c ON c.id=cs.character_id
                WHERE cs.project_id=? AND cs.current_location IS NOT NULL
                  AND c.lifecycle_status IN ('ACTIVE','INACTIVE','MISSING')
                ORDER BY cs.current_location
                """,
                String.class,
                projectId);
        List<String> activeItems = jdbc.queryForList(
                "SELECT item_name FROM item_ownership WHERE project_id=? AND item_status IN ('ACTIVE','DAMAGED') ORDER BY item_name",
                String.class,
                projectId);
        List<String> activeForeshadow = jdbc.queryForList(
                "SELECT title FROM foreshadow WHERE project_id=? AND retrieval_eligible=TRUE AND status IN ('PLANTED','DEVELOPING','DUE','PARTIALLY_RESOLVED') ORDER BY priority DESC,updated_at DESC LIMIT 30",
                String.class,
                projectId);
        List<String> openThreads = jdbc.queryForList(
                "SELECT title FROM foreshadow WHERE project_id=? AND retrieval_eligible=TRUE AND status IN ('CANDIDATE','PLANTED','DEVELOPING','DUE','PARTIALLY_RESOLVED') ORDER BY priority DESC LIMIT 30",
                String.class,
                projectId);
        UUID[] sourceIds = chapters.stream().map(ChapterSnapshot::id).toArray(UUID[]::new);
        String contentHash =
                hash(recentSummary + json.writeValueAsString(openThreads) + json.writeValueAsString(locations));
        Timestamp now = timestamp();
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rolling_outline WHERE project_id=?", Integer.class, projectId);
        if (exists != null && exists > 0) {
            jdbc.update(
                    """
                    UPDATE rolling_outline SET current_chapter_no=?,base_chapter_id=?,from_chapter_no=?,
                        to_chapter_no=?,summary=?,open_threads_json=CAST(? AS jsonb),
                        current_locations_json=CAST(? AS jsonb),active_items_json=CAST(? AS jsonb),
                        active_foreshadow_json=CAST(? AS jsonb),source_chapter_ids=?,content_hash=?,
                        stale=FALSE,version=version+1,updated_at=? WHERE project_id=?
                    """,
                    chapterNo,
                    chapterId,
                    from,
                    chapterNo,
                    recentSummary,
                    json.writeValueAsString(openThreads),
                    json.writeValueAsString(locations),
                    json.writeValueAsString(activeItems),
                    json.writeValueAsString(activeForeshadow),
                    sourceIds,
                    contentHash,
                    now,
                    projectId);
        } else {
            jdbc.update(
                    """
                    INSERT INTO rolling_outline(
                        project_id,current_chapter_no,window_size,summary,goals_json,risks_json,
                        base_chapter_id,from_chapter_no,to_chapter_no,open_threads_json,
                        current_locations_json,active_items_json,active_foreshadow_json,
                        source_chapter_ids,content_hash,stale,updated_at)
                    VALUES (?,?,? ,?,'[]'::jsonb,'[]'::jsonb,?,?,?,CAST(? AS jsonb),
                        CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),?,?,FALSE,?)
                    """,
                    projectId,
                    chapterNo,
                    window,
                    recentSummary,
                    chapterId,
                    from,
                    chapterNo,
                    json.writeValueAsString(openThreads),
                    json.writeValueAsString(locations),
                    json.writeValueAsString(activeItems),
                    json.writeValueAsString(activeForeshadow),
                    sourceIds,
                    contentHash,
                    now);
        }
        long version =
                jdbc.queryForObject("SELECT version FROM rolling_outline WHERE project_id=?", Long.class, projectId);
        jdbc.update(
                """
                INSERT INTO rolling_outline_snapshot(
                    id,project_id,base_chapter_id,from_chapter_no,to_chapter_no,recent_summary,
                    active_conflicts,open_threads,recent_character_changes,current_locations,
                    active_items,active_foreshadow,next_constraints,source_chapter_ids,
                    content_hash,version,created_at)
                VALUES (?,?,?,?,?,?, '[]'::jsonb,CAST(? AS jsonb),'[]'::jsonb,CAST(? AS jsonb),
                    CAST(? AS jsonb),CAST(? AS jsonb),'[]'::jsonb,?,?,?,?)
                """,
                UUID.randomUUID(),
                projectId,
                chapterId,
                from,
                chapterNo,
                recentSummary,
                json.writeValueAsString(openThreads),
                json.writeValueAsString(locations),
                json.writeValueAsString(activeItems),
                json.writeValueAsString(activeForeshadow),
                sourceIds,
                contentHash,
                version,
                now);
    }

    private int currentChapter(UUID projectId) {
        Integer value = jdbc.queryForObject(
                "SELECT COALESCE(MAX(chapter_no),1) FROM chapter WHERE project_id=? AND status='CONFIRMED'",
                Integer.class,
                projectId);
        return value == null ? 1 : Math.max(1, value);
    }

    private void invalidateContextPackets(UUID projectId) {
        Timestamp now = timestamp();
        jdbc.update("UPDATE context_packet SET expires_at=? WHERE project_id=? AND expires_at>?", now, projectId, now);
    }

    private Timestamp timestamp() {
        return Timestamp.from(clock.instant());
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record TemporalStateView(
            int validFromChapterNo,
            Integer validToChapterNo,
            String lifeStatus,
            String currentLocation,
            String physicalCondition,
            String emotionalState,
            String evidence,
            String status) {}

    public record StateSnapshot(
            String lifeStatus,
            String currentLocation,
            String physicalCondition,
            String emotionalState,
            String abilities,
            String inventoryNotes,
            String notes) {}

    public record ItemSnapshot(
            String itemKey, String itemName, UUID ownerCharacterId, String itemStatus, String evidence) {}

    private record ChapterSnapshot(UUID id, int chapterNo, String title, String summary) {}
}
