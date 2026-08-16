package com.storyweaver.worldbook.application;

import com.storyweaver.chapter.repository.ChapterRepository;
import com.storyweaver.character.repository.CharacterRepository;
import com.storyweaver.evolution.application.ProjectEvolutionService;
import com.storyweaver.llm.application.EmbeddingGateway;
import com.storyweaver.llm.application.EmbeddingGateway.EmbeddingResult;
import com.storyweaver.llm.config.RetrievalExperimentMode;
import com.storyweaver.llm.config.RetrievalProperties;
import com.storyweaver.llm.domain.EmbeddingStatus;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.worldbook.application.WorldbookRetrievalRanker.RankedCandidate;
import com.storyweaver.worldbook.domain.Worldbook;
import com.storyweaver.worldbook.domain.WorldbookEntry;
import com.storyweaver.worldbook.domain.WorldbookScope;
import com.storyweaver.worldbook.domain.WorldbookVisibility;
import com.storyweaver.worldbook.repository.WorldbookEntryRepository;
import com.storyweaver.worldbook.repository.WorldbookRepository;
import com.storyweaver.worldbook.repository.WorldbookVectorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorldbookService {
    private final WorldbookRepository worldbooks;
    private final WorldbookEntryRepository entries;
    private final WorldbookVectorRepository vectors;
    private final ProjectAccessService projectAccess;
    private final ChapterRepository chapters;
    private final CharacterRepository characters;
    private final EmbeddingGateway embeddings;
    private final TokenEstimator tokens;
    private final RetrievalProperties retrieval;
    private final MeterRegistry meters;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final ProjectEvolutionService evolution;
    private final WorldbookRetrievalRanker ranker = new WorldbookRetrievalRanker();

    public WorldbookService(
            WorldbookRepository worldbooks,
            WorldbookEntryRepository entries,
            WorldbookVectorRepository vectors,
            ProjectAccessService projectAccess,
            ChapterRepository chapters,
            CharacterRepository characters,
            EmbeddingGateway embeddings,
            TokenEstimator tokens,
            RetrievalProperties retrieval,
            MeterRegistry meters,
            JdbcTemplate jdbc,
            ProjectEvolutionService evolution,
            Clock clock) {
        this.worldbooks = worldbooks;
        this.entries = entries;
        this.vectors = vectors;
        this.projectAccess = projectAccess;
        this.chapters = chapters;
        this.characters = characters;
        this.embeddings = embeddings;
        this.tokens = tokens;
        this.retrieval = retrieval;
        this.meters = meters;
        this.jdbc = jdbc;
        this.evolution = evolution;
        this.clock = clock;
    }

    @Transactional
    public WorldbookEntry create(UUID projectId, UUID ownerId, EntryValues values) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        validateReferences(projectId, values);
        Worldbook worldbook = worldbooks
                .findByProjectId(projectId)
                .orElseGet(() -> worldbooks.save(new Worldbook(
                        projectId, "Default Worldbook", retrieval.worldbookDefaultTokenBudget(), clock.instant())));
        var now = clock.instant();
        WorldbookEntry entry = new WorldbookEntry(
                projectId,
                worldbook.getId(),
                values.title().trim(),
                values.content().trim(),
                values.active(),
                values.constantEnabled(),
                values.vectorEnabled(),
                normalizeKeywords(values.keywords()),
                values.priority(),
                values.scopeType(),
                values.scopeRefId(),
                values.visibilityType(),
                values.visibilityRefId(),
                now);
        float[] vector = applyEmbedding(entry, values.vectorEnabled());
        entries.saveAndFlush(entry);
        refreshRetrievalMetadata(entry.getId(), values.active(), false);
        persistVector(entry, vector);
        linkMatchingReconstructionCandidates(entry);
        return entry;
    }

    @Transactional
    public void cancel(UUID entryId, UUID ownerId) {
        WorldbookEntry entry = requireOwnedEntry(entryId, ownerId);
        UUID projectId = entry.getProjectId();
        vectors.clear(entryId);
        jdbc.update(
                """
                UPDATE project_reconstruction_candidate
                SET status='CANDIDATE',target_entity_id=NULL,retrieval_eligible=TRUE,
                    applied_at=NULL,revoked_at=NULL,revoked_by=NULL,revocation_reason=NULL,
                    inference_type=CASE WHEN inference_type='USER_CONFIRMED' THEN 'MODEL_INFERENCE' ELSE inference_type END,
                    policy_reason='Formal worldbook entry was cancelled; candidate returned to review',updated_at=?
                WHERE target_entity_id=? AND project_id=? AND candidate_type='WORLDBOOK'
                  AND status IN ('APPLIED','REVOKED')
                """,
                java.sql.Timestamp.from(clock.instant()),
                entryId,
                projectId);
        entries.delete(entry);
        entries.flush();
        evolution.invalidate(projectId, "WORLDBOOK", entryId, "WORLDBOOK_ENTRY_CANCELLED");
    }

    @Transactional(readOnly = true)
    public List<WorldbookEntry> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return entries.findAllByProjectIdOrderByPriorityDescTitleAsc(projectId);
    }

    @Transactional
    public WorldbookEntry update(UUID entryId, UUID ownerId, long expectedVersion, EntryValues values) {
        WorldbookEntry entry = requireOwnedEntry(entryId, ownerId);
        if (entry.getVersion() != expectedVersion) {
            throw new ConflictException("optimistic_lock_conflict", "The worldbook entry changed; reload it first");
        }
        validateReferences(entry.getProjectId(), values);
        snapshot(entry.getId());
        entry.revise(
                values.title().trim(),
                values.content().trim(),
                values.active(),
                values.constantEnabled(),
                values.vectorEnabled(),
                normalizeKeywords(values.keywords()),
                values.priority(),
                values.scopeType(),
                values.scopeRefId(),
                values.visibilityType(),
                values.visibilityRefId(),
                clock.instant());
        float[] vector = applyEmbedding(entry, values.vectorEnabled());
        entries.flush();
        refreshRetrievalMetadata(entry.getId(), values.active(), true);
        persistVector(entry, vector);
        evolution.invalidate(entry.getProjectId(), "WORLDBOOK", entry.getId(), "WORLDBOOK_VERSION_CHANGED");
        return entry;
    }

    @Transactional(readOnly = true)
    public ActivationPreview preview(
            UUID projectId,
            UUID ownerId,
            String query,
            UUID chapterId,
            UUID viewpointCharacterId,
            Integer tokenBudget,
            Integer topK) {
        int vectorTopK = topK == null ? retrieval.worldbookCandidatePoolSize() : topK;
        return previewWithOptions(
                projectId,
                ownerId,
                query,
                chapterId,
                viewpointCharacterId,
                tokenBudget,
                new WorldbookRetrievalOptions(
                        retrieval.worldbookMode(),
                        vectorTopK,
                        retrieval.worldbookFinalRankingSize(),
                        retrieval.worldbookRrfRankConstant()));
    }

    @Transactional(readOnly = true)
    public ActivationPreview previewWithOptions(
            UUID projectId,
            UUID ownerId,
            String query,
            UUID chapterId,
            UUID viewpointCharacterId,
            Integer tokenBudget,
            WorldbookRetrievalOptions options) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        validatePreviewReferences(projectId, chapterId, viewpointCharacterId);
        int budget = tokenBudget == null ? retrieval.worldbookDefaultTokenBudget() : tokenBudget;
        Integer chapterNo = chapterId == null
                ? null
                : chapters.findById(chapterId)
                        .map(com.storyweaver.chapter.domain.Chapter::getChapterNo)
                        .orElse(null);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<WorldbookEntry> visibleEntries = entries.findAllByProjectIdOrderByPriorityDescTitleAsc(projectId).stream()
                .filter(WorldbookEntry::isActive)
                .filter(entry -> entry.isRetrievalEligibleAt(chapterNo))
                .filter(entry -> scopeMatches(entry, chapterId, viewpointCharacterId))
                .filter(entry -> visibilityMatches(entry, viewpointCharacterId))
                .toList();
        Map<UUID, Candidate> candidates = new LinkedHashMap<>();
        visibleEntries.forEach(entry -> {
            if (entry.isConstantEnabled()) {
                Candidate constant = candidate(candidates, entry);
                constant.reasons.add("CONSTANT");
                constant.sources.add("CONSTANT");
            }
            Arrays.stream(entry.getKeywords())
                    .filter(keyword -> normalizedQuery.contains(keyword.toLowerCase(Locale.ROOT)))
                    .forEach(keyword -> {
                        Candidate keywordCandidate = candidate(candidates, entry);
                        keywordCandidate.reasons.add("KEYWORD:" + keyword);
                        keywordCandidate.sources.add("KEYWORD");
                        keywordCandidate.keywordScore += 1.0;
                    });
            Candidate existing = candidates.get(entry.getId());
            if (existing != null
                    && existing.sources.contains("KEYWORD")
                    && !entry.getTitle().isBlank()
                    && normalizedQuery.contains(entry.getTitle().toLowerCase(Locale.ROOT))) {
                existing.keywordScore += 2.0;
            }
        });

        EmbeddingResult queryEmbedding = embeddings.embed(query);
        if (queryEmbedding.available()) {
            Map<UUID, WorldbookEntry> byId = visibleEntries.stream()
                    .collect(java.util.stream.Collectors.toMap(WorldbookEntry::getId, entry -> entry));
            vectors.search(projectId, queryEmbedding.vector(), chapterNo, options.candidatePoolSize())
                    .forEach(match -> {
                        WorldbookEntry entry = byId.get(match.entryId());
                        if (entry != null) {
                            Candidate candidate = candidate(candidates, entry);
                            candidate.similarity = match.similarity();
                            candidate.reasons.add(String.format(Locale.ROOT, "VECTOR:%.4f", match.similarity()));
                            candidate.sources.add("VECTOR");
                        }
                    });
        }
        WorldbookRetrievalOptions effectiveOptions = options;
        if (!queryEmbedding.available()
                && (options.mode() == RetrievalExperimentMode.VECTOR_ONLY
                        || options.mode() == RetrievalExperimentMode.HYBRID_FUSION)) {
            effectiveOptions = new WorldbookRetrievalOptions(
                    RetrievalExperimentMode.KEYWORD_ONLY,
                    options.candidatePoolSize(),
                    options.finalRankingSize(),
                    options.rrfRankConstant());
        }

        var ranking = ranker.rank(
                candidates.values().stream()
                        .map(candidate -> new WorldbookRetrievalRanker.RetrievalCandidate(
                                candidate.entry.getId(),
                                candidate.entry.getTitle(),
                                candidate.entry.getPriority(),
                                candidate.entry.isConstantEnabled(),
                                candidate.keywordScore,
                                candidate.similarity,
                                List.copyOf(candidate.sources)))
                        .toList(),
                effectiveOptions);
        List<RankedCandidate> finalDynamic = ranking.rankedCandidates().stream()
                .limit(effectiveOptions.finalRankingSize())
                .toList();
        List<RankedCandidate> contextOrder = new ArrayList<>();
        contextOrder.addAll(ranking.constants());
        Set<UUID> constantIds = ranking.constants().stream()
                .map(RankedCandidate::entryId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        finalDynamic.stream()
                .filter(candidate -> !constantIds.contains(candidate.entryId()))
                .forEach(contextOrder::add);
        Map<UUID, Integer> retrievalRanks = new LinkedHashMap<>();
        List<RankedCandidate> retrievalOrder = finalDynamic;
        for (int index = 0; index < retrievalOrder.size(); index++) {
            retrievalRanks.put(retrievalOrder.get(index).entryId(), index + 1);
        }
        Map<UUID, RankedCandidate> allRanked = new LinkedHashMap<>();
        ranking.constants().forEach(candidate -> allRanked.put(candidate.entryId(), candidate));
        ranking.rankedCandidates().forEach(candidate -> allRanked.put(candidate.entryId(), candidate));
        candidates
                .values()
                .forEach(candidate -> allRanked.computeIfAbsent(
                        candidate.entry.getId(),
                        ignored -> new RankedCandidate(
                                candidate.entry.getId(),
                                candidate.entry.getTitle(),
                                candidate.entry.getPriority(),
                                candidate.entry.isConstantEnabled(),
                                candidate.keywordScore,
                                candidate.similarity,
                                0.0,
                                List.copyOf(candidate.sources))));
        Set<UUID> rankedByModeIds = new LinkedHashSet<>();
        ranking.constants().forEach(candidate -> rankedByModeIds.add(candidate.entryId()));
        ranking.rankedCandidates().forEach(candidate -> rankedByModeIds.add(candidate.entryId()));
        Map<UUID, WorldbookEntry> entriesById = visibleEntries.stream()
                .collect(java.util.stream.Collectors.toMap(WorldbookEntry::getId, entry -> entry));
        int remaining = budget;
        int selectedTokens = 0;
        List<ActivationReport> reports = new ArrayList<>();
        List<ActivatedEntry> selected = new ArrayList<>();
        Set<UUID> selectedIds = new LinkedHashSet<>();
        for (RankedCandidate candidate : contextOrder) {
            WorldbookEntry entry = entriesById.get(candidate.entryId());
            int estimated = tokens.estimate(entry.getTitle(), entry.getContent());
            boolean fits = estimated <= remaining;
            if (fits) {
                remaining -= estimated;
                selectedTokens += estimated;
                selected.add(new ActivatedEntry(entry.getId(), entry.getTitle(), entry.getContent(), estimated));
                selectedIds.add(entry.getId());
            }
            Candidate source = candidates.get(entry.getId());
            source.reasons.forEach(reason -> meters.counter(
                            "storyweaver.worldbook.activations", "status", fits ? "SELECTED" : "DROPPED")
                    .increment());
        }
        for (RankedCandidate candidate : allRanked.values()) {
            WorldbookEntry entry = entriesById.get(candidate.entryId());
            Candidate source = candidates.get(candidate.entryId());
            boolean inFinalRanking =
                    contextOrder.stream().anyMatch(item -> item.entryId().equals(candidate.entryId()));
            boolean selectedForContext = selectedIds.contains(candidate.entryId());
            boolean rankedByMode = rankedByModeIds.contains(candidate.entryId());
            int estimated = tokens.estimate(entry.getTitle(), entry.getContent());
            reports.add(new ActivationReport(
                    entry.getId(),
                    entry.getTitle(),
                    List.copyOf(source.reasons),
                    entry.getPriority(),
                    candidate.constant(),
                    candidate.keywordScore(),
                    candidate.vectorScore(),
                    candidate.finalScore(),
                    List.copyOf(candidate.sources()),
                    retrievalRanks.get(candidate.entryId()),
                    estimated,
                    inFinalRanking,
                    selectedForContext,
                    !rankedByMode
                            ? "MODE_FILTERED"
                            : (!inFinalRanking
                                    ? "FINAL_RANKING_LIMIT"
                                    : (selectedForContext ? null : "TOKEN_BUDGET"))));
        }
        List<ActivatedEntry> retrieved = retrievalOrder.stream()
                .filter(candidate -> selectedIds.contains(candidate.entryId()))
                .map(candidate -> {
                    WorldbookEntry entry = entriesById.get(candidate.entryId());
                    return new ActivatedEntry(
                            entry.getId(),
                            entry.getTitle(),
                            entry.getContent(),
                            tokens.estimate(entry.getTitle(), entry.getContent()));
                })
                .toList();
        return new ActivationPreview(
                projectId,
                budget,
                selectedTokens,
                queryEmbedding.available(),
                queryEmbedding.unavailableReason(),
                options.mode(),
                effectiveOptions.mode(),
                effectiveOptions.candidatePoolSize(),
                effectiveOptions.finalRankingSize(),
                ranking.rawCandidateCount(),
                ranking.deduplicatedCandidateCount(),
                List.copyOf(selected),
                List.copyOf(retrieved),
                List.copyOf(reports));
    }

    private WorldbookEntry requireOwnedEntry(UUID entryId, UUID ownerId) {
        WorldbookEntry entry = entries.findById(entryId)
                .orElseThrow(() -> new NotFoundException("worldbook_entry_not_found", "Worldbook entry was not found"));
        projectAccess.requireOwnedProject(entry.getProjectId(), ownerId);
        return entry;
    }

    private float[] applyEmbedding(WorldbookEntry entry, boolean vectorEnabled) {
        if (!vectorEnabled) {
            entry.embedding(EmbeddingStatus.NOT_REQUESTED, null, clock.instant());
            return null;
        }
        EmbeddingResult result = embeddings.embed(entry.getTitle() + "\n" + entry.getContent());
        entry.embedding(
                result.available() ? EmbeddingStatus.AVAILABLE : EmbeddingStatus.UNAVAILABLE,
                result.model(),
                clock.instant());
        return result.available() ? result.vector() : null;
    }

    private void persistVector(WorldbookEntry entry, float[] vector) {
        if (entry.getEmbeddingStatus() == EmbeddingStatus.AVAILABLE && vector != null) {
            try {
                vectors.write(entry.getId(), vector);
            } catch (RuntimeException exception) {
                entry.embedding(EmbeddingStatus.UNAVAILABLE, entry.getEmbeddingModel(), clock.instant());
                vectors.clear(entry.getId());
                entries.flush();
            }
        } else {
            vectors.clear(entry.getId());
        }
    }

    private void linkMatchingReconstructionCandidates(WorldbookEntry entry) {
        var now = java.sql.Timestamp.from(clock.instant());
        jdbc.update(
                """
                UPDATE project_reconstruction_candidate
                SET status='APPLIED',target_entity_id=?,applied_at=COALESCE(applied_at,?),
                    retrieval_eligible=TRUE,revoked_at=NULL,revoked_by=NULL,revocation_reason=NULL,
                    policy_reason='Loaded and saved as a formal worldbook entry',updated_at=?
                WHERE project_id=? AND candidate_type='WORLDBOOK'
                  AND btrim(content)=btrim(?) AND status IN ('CANDIDATE','ACCEPTED','REVOKED')
                """,
                entry.getId(),
                now,
                now,
                entry.getProjectId(),
                entry.getContent());
    }

    private void snapshot(UUID entryId) {
        jdbc.update(
                """
                INSERT INTO worldbook_entry_version(
                    id,entry_id,project_id,version_no,title,content,valid_from_chapter_no,
                    valid_to_chapter_no,lifecycle_status,content_hash,created_at)
                SELECT gen_random_uuid(),id,project_id,version,title,content,valid_from_chapter_no,
                    valid_to_chapter_no,lifecycle_status,coalesce(content_hash,encode(digest(title || E'\\n' || content,'sha256'),'hex')),now()
                FROM worldbook_entry WHERE id=?
                ON CONFLICT(entry_id,version_no) DO NOTHING
                """,
                entryId);
    }

    private void refreshRetrievalMetadata(UUID entryId, boolean active, boolean incrementEmbeddingVersion) {
        jdbc.update(
                """
                UPDATE worldbook_entry
                SET retrieval_eligible=?,lifecycle_status=?,
                    content_hash=encode(digest(title || E'\\n' || content,'sha256'),'hex'),
                    embedding_version=embedding_version + ?
                WHERE id=?
                """,
                active,
                active ? "ACTIVE" : "ARCHIVED",
                incrementEmbeddingVersion ? 1 : 0,
                entryId);
    }

    private Candidate candidate(Map<UUID, Candidate> candidates, WorldbookEntry entry) {
        return candidates.computeIfAbsent(entry.getId(), ignored -> new Candidate(entry));
    }

    private void validateReferences(UUID projectId, EntryValues values) {
        if (!values.constantEnabled() && !values.vectorEnabled() && normalizeKeywords(values.keywords()).length == 0) {
            throw new BadRequestException("worldbook_activation_required", "At least one activation mode is required");
        }
        switch (values.scopeType()) {
            case PROJECT -> requireNull(values.scopeRefId(), "Project scope must not have scopeRefId");
            case CHAPTER -> requireChapter(projectId, values.scopeRefId());
            case CHARACTER -> requireCharacter(projectId, values.scopeRefId());
        }
        switch (values.visibilityType()) {
            case ALL, AUTHOR_ONLY ->
                requireNull(values.visibilityRefId(), "This visibility must not have visibilityRefId");
            case CHARACTER_ONLY -> requireCharacter(projectId, values.visibilityRefId());
        }
    }

    private void validatePreviewReferences(UUID projectId, UUID chapterId, UUID viewpointCharacterId) {
        if (chapterId != null) requireChapter(projectId, chapterId);
        if (viewpointCharacterId != null) requireCharacter(projectId, viewpointCharacterId);
    }

    private void requireChapter(UUID projectId, UUID chapterId) {
        if (chapterId == null
                || chapters.findById(chapterId)
                        .filter(chapter -> chapter.getProjectId().equals(projectId))
                        .isEmpty()) {
            throw new NotFoundException("chapter_not_found", "Chapter was not found in this project");
        }
    }

    private void requireCharacter(UUID projectId, UUID characterId) {
        if (characterId == null
                || characters
                        .findById(characterId)
                        .filter(character -> character.getProjectId().equals(projectId))
                        .isEmpty()) {
            throw new NotFoundException("character_not_found", "Character was not found in this project");
        }
    }

    private void requireNull(UUID value, String message) {
        if (value != null) throw new BadRequestException("invalid_worldbook_scope", message);
    }

    private boolean scopeMatches(WorldbookEntry entry, UUID chapterId, UUID viewpointCharacterId) {
        return switch (entry.getScopeType()) {
            case PROJECT -> true;
            case CHAPTER -> entry.getScopeRefId().equals(chapterId);
            case CHARACTER -> entry.getScopeRefId().equals(viewpointCharacterId);
        };
    }

    private boolean visibilityMatches(WorldbookEntry entry, UUID viewpointCharacterId) {
        return entry.getVisibilityType() != WorldbookVisibility.CHARACTER_ONLY
                || entry.getVisibilityRefId().equals(viewpointCharacterId);
    }

    private String[] normalizeKeywords(List<String> values) {
        if (values == null) return new String[0];
        return values.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toArray(String[]::new);
    }

    public record EntryValues(
            String title,
            String content,
            boolean active,
            boolean constantEnabled,
            boolean vectorEnabled,
            List<String> keywords,
            int priority,
            WorldbookScope scopeType,
            UUID scopeRefId,
            WorldbookVisibility visibilityType,
            UUID visibilityRefId) {}

    public record ActivatedEntry(UUID entryId, String title, String content, int estimatedTokens) {}

    public record ActivationReport(
            UUID entryId,
            String title,
            List<String> reasons,
            int priority,
            boolean constant,
            double keywordScore,
            double vectorScore,
            double finalScore,
            List<String> sources,
            Integer retrievalRank,
            int estimatedTokens,
            boolean inFinalRanking,
            boolean selected,
            String dropReason) {}

    public record ActivationPreview(
            UUID projectId,
            int tokenBudget,
            int selectedTokens,
            boolean embeddingAvailable,
            String degradedReason,
            RetrievalExperimentMode requestedMode,
            RetrievalExperimentMode mode,
            int candidatePoolSize,
            int finalRankingSize,
            int rawCandidateCount,
            int deduplicatedCandidateCount,
            List<ActivatedEntry> selectedEntries,
            List<ActivatedEntry> retrievedEntries,
            List<ActivationReport> reports) {}

    private static final class Candidate {
        private final WorldbookEntry entry;
        private final Set<String> reasons = new LinkedHashSet<>();
        private final Set<String> sources = new LinkedHashSet<>();
        private double keywordScore;
        private double similarity;

        private Candidate(WorldbookEntry entry) {
            this.entry = entry;
        }
    }
}
