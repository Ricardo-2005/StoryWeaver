package com.storyweaver.importing.book.application;

import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.application.AgentContracts.ExtractionResult;
import com.storyweaver.llm.application.ExtractorGateway;
import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.usage.application.PricingService;
import com.storyweaver.usage.application.PricingService.TokenUsage;
import com.storyweaver.usage.application.UsageAttributionContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class BookReconstructionService {
    static final String ANALYSIS_VERSION = "book-reconstruction-v1";
    static final String PROMPT_VERSION = "reconstruction-prompt-v1";
    private static final Set<String> ACTIVE = Set.of(
            "QUEUED",
            "PREPROCESSING",
            "CHAPTER_ANALYSIS",
            "VOLUME_AGGREGATION",
            "ENTITY_RESOLUTION",
            "GLOBAL_RECONSTRUCTION",
            "FORESHADOW_ANALYSIS",
            "SKILL_DISTILLATION",
            "VALIDATING",
            "APPLYING");
    private static final Set<String> STOPPED = Set.of("PAUSED", "PAUSED_BUDGET", "CANCELLED");

    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccess;
    private final ExtractorGateway extractor;
    private final PricingService pricing;
    private final UsageAttributionContext attribution;
    private final ReconstructionCandidatePolicyEngine candidatePolicy;
    private final ReconstructionCharacterMaterializer characterMaterializer;
    private final ReconstructionProjectAssetMaterializer projectAssetMaterializer;
    private final ExecutorService executor;
    private final ObjectMapper json;
    private final Clock clock;

    public BookReconstructionService(
            JdbcTemplate jdbc,
            ProjectAccessService projectAccess,
            ExtractorGateway extractor,
            PricingService pricing,
            UsageAttributionContext attribution,
            ReconstructionCandidatePolicyEngine candidatePolicy,
            ReconstructionCharacterMaterializer characterMaterializer,
            ReconstructionProjectAssetMaterializer projectAssetMaterializer,
            ExecutorService aiTaskExecutor,
            ObjectMapper json,
            Clock clock) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
        this.extractor = extractor;
        this.pricing = pricing;
        this.attribution = attribution;
        this.candidatePolicy = candidatePolicy;
        this.characterMaterializer = characterMaterializer;
        this.projectAssetMaterializer = projectAssetMaterializer;
        this.executor = aiTaskExecutor;
        this.json = json;
        this.clock = clock;
    }

    public Estimate estimate(
            UUID projectId, UUID ownerId, Mode mode, boolean includeSkill, boolean includeForeshadowing) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        ImportSource source = source(projectId, ownerId);
        int chunkSize = mode.chunkCharacters();
        Integer chunks = jdbc.queryForObject(
                "SELECT COALESCE(SUM(CEIL(GREATEST(character_count,1)::numeric / ?)),0)::int "
                        + "FROM book_import_chapter WHERE import_id=? AND included=TRUE",
                Integer.class,
                chunkSize,
                source.importId());
        int totalChunks = chunks == null ? 0 : chunks;
        int aggregationCalls = Math.max(1, (source.totalChapters() + 39) / 40);
        int globalCalls = 2 + (includeForeshadowing ? 1 : 0) + (includeSkill ? 1 : 0);
        int calls = totalChunks + aggregationCalls + globalCalls;
        long inputTokens = Math.max(1, source.totalCharacters() / 3) + (long) calls * mode.promptOverhead();
        long outputTokens = (long) calls * mode.outputTokens();
        String model = DeepSeekAgent.EXTRACTOR.model();
        var maximum = pricing.price(model, tokenUsage(inputTokens, outputTokens), clock.instant());
        var minimum = pricing.price(model, tokenUsage(inputTokens * 9 / 10, outputTokens / 2), clock.instant());
        return new Estimate(
                mode,
                source.totalChapters(),
                totalChunks,
                calls,
                inputTokens,
                outputTokens,
                minimum.amount(),
                maximum.amount(),
                maximum.currency(),
                model,
                !maximum.priced());
    }

    public JobView start(UUID projectId, UUID ownerId, StartRequest request) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        if (request.maxBudget() != null && request.maxBudget().signum() < 0) {
            throw new BadRequestException("RECONSTRUCTION_BUDGET_INVALID", "Maximum budget cannot be negative");
        }
        if (hasActive(projectId)) {
            throw new ConflictException(
                    "RECONSTRUCTION_ACTIVE", "This project already has an active reconstruction job");
        }
        ImportSource source = source(projectId, ownerId);
        Estimate estimate = estimate(
                projectId, ownerId, request.mode(), request.includeSkillDistillation(), request.includeForeshadowing());
        UUID jobId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO book_reconstruction_job(
                    id,import_id,project_id,owner_id,mode,status,current_step,
                    include_skill_distillation,include_foreshadowing,total_chapters,
                    estimated_calls,estimated_input_tokens,estimated_output_tokens,
                    estimated_cost_min,estimated_cost_max,estimate_currency,max_budget,
                    analysis_version,prompt_version,model,started_at,created_at,updated_at)
                VALUES (?,?,?,?,?,'QUEUED','PREPROCESSING',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                jobId,
                source.importId(),
                projectId,
                ownerId,
                request.mode().name(),
                request.includeSkillDistillation(),
                request.includeForeshadowing(),
                source.totalChapters(),
                estimate.estimatedCalls(),
                estimate.estimatedInputTokens(),
                estimate.estimatedOutputTokens(),
                estimate.estimatedCostMin(),
                estimate.estimatedCostMax(),
                estimate.currency(),
                request.maxBudget(),
                ANALYSIS_VERSION,
                PROMPT_VERSION,
                estimate.model(),
                timestamp(now),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "UPDATE novel_project SET reconstruction_status='ANALYZING',updated_at=? WHERE id=?",
                timestamp(now),
                projectId);
        submit(jobId);
        return get(projectId, ownerId);
    }

    public JobView get(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        JobRow row = latest(projectId, ownerId);
        if (row == null) {
            ImportSource source = source(projectId, ownerId);
            return JobView.notAnalyzed(projectId, source.totalChapters());
        }
        refreshUsage(row.id());
        row = requireJob(row.id(), ownerId);
        Counts counts = counts(row.id());
        return view(row, counts);
    }

    @Transactional
    public List<CandidateView> candidates(UUID projectId, UUID ownerId, String status, String type) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        JobRow job = latest(projectId, ownerId);
        if (job == null) return List.of();
        candidatePolicy.classify(job.id(), projectId);
        StringBuilder sql = new StringBuilder("SELECT * FROM project_reconstruction_candidate WHERE job_id=?");
        List<Object> args = new ArrayList<>();
        args.add(job.id());
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            args.add(status.toUpperCase(Locale.ROOT));
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND candidate_type=?");
            args.add(type.toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY safe_to_apply DESC,confidence,created_at,id LIMIT 1000");
        return jdbc.query(sql.toString(), (rs, index) -> candidate(rs), args.toArray());
    }

    public JobView pause(UUID projectId, UUID ownerId) {
        JobRow job = requireLatest(projectId, ownerId);
        if (!ACTIVE.contains(job.status())) throw new ConflictException("RECONSTRUCTION_STATE", "Job is not running");
        jdbc.update(
                "UPDATE book_reconstruction_job SET pause_requested=TRUE,version=version+1,updated_at=? WHERE id=?",
                timestamp(),
                job.id());
        return get(projectId, ownerId);
    }

    public JobView resume(UUID projectId, UUID ownerId, BigDecimal maxBudget) {
        JobRow job = requireLatest(projectId, ownerId);
        if (!Set.of("PAUSED", "PAUSED_BUDGET", "PARTIAL", "FAILED").contains(job.status())) {
            throw new ConflictException("RECONSTRUCTION_STATE", "Job cannot be resumed from its current state");
        }
        if ("PAUSED_BUDGET".equals(job.status()) && (maxBudget == null || maxBudget.compareTo(job.actualCost()) <= 0)) {
            throw new BadRequestException(
                    "RECONSTRUCTION_BUDGET_REQUIRED", "Increase the maximum budget before resuming");
        }
        jdbc.update(
                "UPDATE book_reconstruction_job SET status='QUEUED',pause_requested=FALSE,cancel_requested=FALSE,max_budget=COALESCE(?,max_budget),error_code=NULL,error_message=NULL,version=version+1,updated_at=? WHERE id=?",
                maxBudget,
                timestamp(),
                job.id());
        jdbc.update(
                "UPDATE novel_project SET reconstruction_status='ANALYZING',updated_at=? WHERE id=?",
                timestamp(),
                projectId);
        submit(job.id());
        return get(projectId, ownerId);
    }

    public JobView cancel(UUID projectId, UUID ownerId) {
        JobRow job = requireLatest(projectId, ownerId);
        if (Set.of("COMPLETED", "CANCELLED").contains(job.status())) return get(projectId, ownerId);
        jdbc.update(
                "UPDATE book_reconstruction_job SET cancel_requested=TRUE,version=version+1,updated_at=? WHERE id=?",
                timestamp(),
                job.id());
        if (!ACTIVE.contains(job.status())) markCancelled(job.id(), projectId);
        return get(projectId, ownerId);
    }

    public JobView retryFailed(UUID projectId, UUID ownerId) {
        JobRow job = requireLatest(projectId, ownerId);
        int failed = jdbc.update(
                "UPDATE book_analysis_chunk SET status='PENDING',error_message=NULL WHERE job_id=? AND status='FAILED'",
                job.id());
        if (failed == 0)
            throw new ConflictException("RECONSTRUCTION_NO_FAILURES", "There are no failed chunks to retry");
        jdbc.update(
                "UPDATE book_reconstruction_job SET status='QUEUED',failed_chapters=0,retry_count=retry_count+1,error_code=NULL,error_message=NULL,updated_at=? WHERE id=?",
                timestamp(),
                job.id());
        submit(job.id());
        return get(projectId, ownerId);
    }

    public CandidateView decide(UUID projectId, UUID candidateId, UUID ownerId, boolean approve) {
        JobRow job = requireLatest(projectId, ownerId);
        if (!approve) {
            int reset = jdbc.update(
                    "UPDATE project_reconstruction_candidate SET status='CANDIDATE',inference_type=CASE WHEN inference_type='USER_CONFIRMED' THEN 'MODEL_INFERENCE' ELSE inference_type END,updated_at=? WHERE id=? AND job_id=? AND status='ACCEPTED'",
                    timestamp(),
                    candidateId,
                    job.id());
            if (reset > 0) {
                return jdbc.queryForObject(
                        "SELECT * FROM project_reconstruction_candidate WHERE id=?",
                        (rs, index) -> candidate(rs),
                        candidateId);
            }
        }
        int updated = jdbc.update(
                "UPDATE project_reconstruction_candidate SET status=?,inference_type=CASE WHEN ? THEN 'USER_CONFIRMED' ELSE inference_type END,updated_at=? WHERE id=? AND job_id=? AND status IN ('CANDIDATE','CONFLICT')",
                approve ? "ACCEPTED" : "REJECTED",
                approve,
                timestamp(),
                candidateId,
                job.id());
        if (updated == 0)
            throw new NotFoundException(
                    "RECONSTRUCTION_CANDIDATE_NOT_FOUND", "Candidate was not found or cannot change this decision");
        return jdbc.queryForObject(
                "SELECT * FROM project_reconstruction_candidate WHERE id=?", (rs, index) -> candidate(rs), candidateId);
    }

    public CandidateView restoreRejectedCandidate(UUID projectId, UUID candidateId, UUID ownerId) {
        JobRow job = requireLatest(projectId, ownerId);
        int updated = jdbc.update(
                """
                UPDATE project_reconstruction_candidate
                SET status='CANDIDATE',inference_type=CASE WHEN inference_type='USER_CONFIRMED'
                        THEN 'MODEL_INFERENCE' ELSE inference_type END,
                    retrieval_eligible=TRUE,
                    policy_reason=CASE WHEN candidate_type='CHARACTER'
                        THEN 'Restored by the user for character review' ELSE policy_reason END,
                    updated_at=?
                WHERE id=? AND job_id=? AND status='REJECTED'
                """,
                timestamp(),
                candidateId,
                job.id());
        if (updated == 0) {
            throw new ConflictException(
                    "RECONSTRUCTION_CANDIDATE_NOT_RESTORABLE", "Only a rejected candidate can be restored");
        }
        return jdbc.queryForObject(
                "SELECT * FROM project_reconstruction_candidate WHERE id=?", (rs, index) -> candidate(rs), candidateId);
    }

    public CandidateView revoke(UUID projectId, UUID candidateId, UUID ownerId, String reason) {
        JobRow job = requireLatest(projectId, ownerId);
        int updated = jdbc.update(
                "UPDATE project_reconstruction_candidate SET status='REVOKED',retrieval_eligible=FALSE,revoked_at=?,revoked_by=?,revocation_reason=?,updated_at=? WHERE id=? AND job_id=? AND status IN ('ACCEPTED','APPLIED')",
                timestamp(),
                ownerId,
                reason.strip(),
                timestamp(),
                candidateId,
                job.id());
        if (updated == 0) {
            throw new ConflictException(
                    "RECONSTRUCTION_CANDIDATE_NOT_REVOCABLE", "Only an accepted or applied candidate can be revoked");
        }
        jdbc.update(
                "UPDATE chapter_reconstruction_metadata SET lifecycle_status='STALE',updated_at=? WHERE source_candidate_id=?",
                timestamp(),
                candidateId);
        return jdbc.queryForObject(
                "SELECT * FROM project_reconstruction_candidate WHERE id=?", (rs, index) -> candidate(rs), candidateId);
    }

    public JobView approveSafe(UUID projectId, UUID ownerId) {
        JobRow job = requireLatest(projectId, ownerId);
        jdbc.update(
                "UPDATE book_reconstruction_job SET status='APPLYING',current_step='APPLYING',updated_at=? WHERE id=?",
                timestamp(),
                job.id());
        jdbc.update(
                "UPDATE project_reconstruction_candidate SET status='ACCEPTED',inference_type='USER_CONFIRMED',updated_at=? WHERE job_id=? AND status='CANDIDATE' AND safe_to_apply=TRUE",
                timestamp(),
                job.id());
        jdbc.update(
                """
                INSERT INTO chapter_reconstruction_metadata(chapter_id,project_id,summary,metadata,analysis_version,source_candidate_id,updated_at)
                SELECT chapter_id,project_id,string_agg(content,E'\n' ORDER BY created_at),'{}'::jsonb,?,(array_agg(id ORDER BY created_at))[1],?
                FROM project_reconstruction_candidate
                WHERE job_id=? AND candidate_type='CHAPTER_SUMMARY' AND status='ACCEPTED' AND chapter_id IS NOT NULL
                GROUP BY chapter_id,project_id
                ON CONFLICT(chapter_id) DO UPDATE SET summary=EXCLUDED.summary,analysis_version=EXCLUDED.analysis_version,
                    source_candidate_id=EXCLUDED.source_candidate_id,updated_at=EXCLUDED.updated_at
                """,
                ANALYSIS_VERSION,
                timestamp(),
                job.id());
        jdbc.update(
                "UPDATE project_reconstruction_candidate SET status='APPLIED',applied_at=?,updated_at=? WHERE job_id=? AND status='ACCEPTED' AND safe_to_apply=TRUE",
                timestamp(),
                timestamp(),
                job.id());
        completeIfReviewed(job.id(), projectId);
        return get(projectId, ownerId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recover();
    }

    @Scheduled(fixedDelayString = "${storyweaver.import.reconstruction.recovery-interval:30s}")
    public void recover() {
        Instant staleBefore = clock.instant().minusSeconds(10 * 60);
        jdbc.query(
                "SELECT id FROM book_reconstruction_job WHERE status='QUEUED' OR (status IN ('PREPROCESSING','CHAPTER_ANALYSIS','VOLUME_AGGREGATION','ENTITY_RESOLUTION','GLOBAL_RECONSTRUCTION','FORESHADOW_ANALYSIS','SKILL_DISTILLATION','VALIDATING') AND updated_at < ?)",
                rs -> {
                    while (rs.next()) {
                        UUID id = rs.getObject(1, UUID.class);
                        jdbc.update(
                                "UPDATE book_reconstruction_job SET status='QUEUED',updated_at=? WHERE id=?",
                                timestamp(),
                                id);
                        submit(id);
                    }
                    return null;
                },
                timestamp(staleBefore));
        materializeAwaitingCharacters();
        materializeAwaitingProjectAssets();
    }

    private void submit(UUID jobId) {
        executor.submit(() -> run(jobId));
    }

    private void run(UUID jobId) {
        JobRow job = job(jobId);
        if (job == null || (!"QUEUED".equals(job.status()) && !ACTIVE.contains(job.status()))) return;
        try (var ignored = attribution.reconstruction(jobId)) {
            if (control(job)) return;
            preprocess(job);
            job = job(jobId);
            if (control(job)) return;
            analyzeChapters(job);
            job = job(jobId);
            if (control(job)) return;
            if (failedChunks(jobId) > 0) {
                finishPartial(job);
                return;
            }
            aggregate(
                    job,
                    "VOLUME_AGGREGATION",
                    "OUTLINE",
                    "Create a reverse outline only from these chapter summaries. Do not invent future plot.");
            if (control(job(jobId))) return;
            aggregate(
                    job,
                    "ENTITY_RESOLUTION",
                    "ENTITY_RESOLUTION",
                    "Resolve aliases conservatively across these candidates. Mark uncertain merges as NEEDS_REVIEW in the text.");
            if (control(job(jobId))) return;
            aggregate(
                    job,
                    "GLOBAL_RECONSTRUCTION",
                    "PROJECT_OVERVIEW",
                    "Build an evidence-limited project overview and final imported story state. Do not overwrite user fields or invent later plot.");
            if (job.includeForeshadowing()) {
                if (control(job(jobId))) return;
                aggregate(
                        job,
                        "FORESHADOW_ANALYSIS",
                        "FORESHADOW",
                        "Find only cross-chapter foreshadow candidates supported by the supplied summaries; avoid treating atmosphere as foreshadowing.");
            }
            if (job.includeSkill()) {
                if (control(job(jobId))) return;
                aggregate(
                        job,
                        "SKILL_DISTILLATION",
                        "SKILL",
                        "Extract high-level project-local writing rules with evidence. Return draft candidates only.");
            }
            transition(jobId, "VALIDATING");
            deriveForeshadowCandidates(job);
            validateCandidates(jobId);
            candidatePolicy.classify(jobId, job.projectId());
            var materialized = characterMaterializer.materialize(jobId, job.projectId(), job.ownerId());
            candidatePolicy.classify(jobId, job.projectId());
            var projectAssets = projectAssetMaterializer.materialize(jobId, job.projectId(), job.ownerId());
            refreshUsage(jobId);
            Counts counts = counts(jobId);
            String finalStatus = counts.pending() > 0 || counts.conflicts() > 0 ? "WAITING_REVIEW" : "COMPLETED";
            finishValidation(jobId, finalStatus);
            jdbc.update(
                    "UPDATE novel_project SET reconstruction_status=?,updated_at=? WHERE id=?",
                    "WAITING_REVIEW".equals(finalStatus) ? "REVIEW_REQUIRED" : "READY",
                    timestamp(),
                    job.projectId());
            step(
                    jobId,
                    "VALIDATING",
                    "COMPLETED",
                    counts.total(),
                    counts.total(),
                    "Candidate evidence checked; automatically created " + materialized.created()
                            + " character cards, " + projectAssets.worldbookEntries() + " worldbook entries, "
                            + (projectAssets.rollingOutline() ? 1 : 0) + " rolling outlines and "
                            + projectAssets.foreshadowEntries() + " foreshadow entries");
        } catch (ControlledStop ignored) {
            // State was persisted by the control check.
        } catch (RuntimeException exception) {
            refreshUsage(jobId);
            JobRow current = job(jobId);
            if (current != null && !STOPPED.contains(current.status())) {
                jdbc.update(
                        "UPDATE book_reconstruction_job SET status='FAILED',error_code='RECONSTRUCTION_FAILED',error_message=?,updated_at=?,version=version+1 WHERE id=?",
                        safeMessage(exception),
                        timestamp(),
                        jobId);
                jdbc.update(
                        "UPDATE novel_project SET reconstruction_status='PARTIAL',updated_at=? WHERE id=?",
                        timestamp(),
                        current.projectId());
                step(
                        jobId,
                        current.currentStep(),
                        "FAILED",
                        current.processedChunks(),
                        current.totalChunks(),
                        safeMessage(exception));
            }
        }
    }

    private void preprocess(JobRow job) {
        Integer existing =
                jdbc.queryForObject("SELECT COUNT(*) FROM book_analysis_chunk WHERE job_id=?", Integer.class, job.id());
        if (existing != null && existing > 0) return;
        transition(job.id(), "PREPROCESSING");
        int[] sequence = {0};
        List<ChapterRef> chapters = jdbc.query(
                "SELECT c.id,bic.sequence_no FROM book_import_chapter bic JOIN chapter c ON c.id=bic.created_chapter_id WHERE bic.import_id=? AND bic.included=TRUE ORDER BY bic.sequence_no",
                (rs, index) -> new ChapterRef(rs.getObject(1, UUID.class), rs.getInt(2)),
                job.importId());
        for (ChapterRef chapter : chapters) {
            try (Reader reader = chapterReader(chapter.id())) {
                long offset = 0;
                for (String text : chunks(reader, Mode.valueOf(job.mode()).chunkCharacters())) {
                    long end = offset + text.length();
                    jdbc.update(
                            "INSERT INTO book_analysis_chunk(id,job_id,chapter_id,sequence_no,chapter_index,start_offset,end_offset,text_hash,token_estimate,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                            UUID.randomUUID(),
                            job.id(),
                            chapter.id(),
                            ++sequence[0],
                            chapter.index(),
                            offset,
                            end,
                            sha256(text),
                            Math.max(1, text.length() / 3),
                            timestamp());
                    offset = end;
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Chapter preprocessing failed", exception);
            }
        }
        jdbc.update(
                "UPDATE book_reconstruction_job SET total_chunks=?,current_step='CHAPTER_ANALYSIS',updated_at=? WHERE id=?",
                sequence[0],
                timestamp(),
                job.id());
        step(job.id(), "PREPROCESSING", "COMPLETED", sequence[0], sequence[0], "Deterministic chapter anchors created");
    }

    private void analyzeChapters(JobRow job) {
        transition(job.id(), "CHAPTER_ANALYSIS");
        List<ChunkRef> pending = jdbc.query(
                "SELECT * FROM book_analysis_chunk WHERE job_id=? AND status IN ('PENDING','FAILED') ORDER BY sequence_no",
                (rs, index) -> chunk(rs),
                job.id());
        for (ChunkRef chunk : pending) {
            if (control(job(job.id()))) throw new ControlledStop();
            checkBudget(job.id());
            jdbc.update(
                    "UPDATE book_analysis_chunk SET status='PROCESSING',attempt_count=attempt_count+1,error_message=NULL WHERE id=?",
                    chunk.id());
            try {
                String text = readChunk(chunk.chapterId(), chunk.startOffset(), chunk.endOffset());
                ExtractionResult result = extractor.extract(
                        job.projectId(),
                        job.ownerId(),
                        new AgentInput(chapterInstruction(Mode.valueOf(job.mode())), text));
                saveLocalResult(job, chunk, result);
                jdbc.update(
                        "UPDATE book_analysis_chunk SET status='COMPLETED',processed_at=? WHERE id=?",
                        timestamp(),
                        chunk.id());
            } catch (RuntimeException exception) {
                jdbc.update(
                        "UPDATE book_analysis_chunk SET status='FAILED',error_message=? WHERE id=?",
                        safeMessage(exception),
                        chunk.id());
            }
            updateProgress(job.id());
            refreshUsage(job.id());
        }
        step(
                job.id(),
                "CHAPTER_ANALYSIS",
                "COMPLETED",
                completedChunks(job.id()),
                totalChunks(job.id()),
                "Chapter/chunk extraction finished");
    }

    private void saveLocalResult(JobRow job, ChunkRef chunk, ExtractionResult result) {
        Evidence evidence = new Evidence(
                chunk.chapterId(), chunk.chapterIndex(), chunk.startOffset(), chunk.endOffset(), chunk.textHash());
        insertCandidate(job, chunk, "CHAPTER_SUMMARY", result.summary(), "HIGH", "MODEL_INFERENCE", true, evidence);
        result.events()
                .forEach(value ->
                        insertCandidate(job, chunk, "EVENT", value, "MEDIUM", "MODEL_INFERENCE", true, evidence));
        result.characterChanges()
                .forEach(value ->
                        insertCandidate(job, chunk, "CHARACTER", value, "MEDIUM", "MODEL_INFERENCE", false, evidence));
        result.candidateFacts()
                .forEach(value ->
                        insertCandidate(job, chunk, "WORLDBOOK", value, "MEDIUM", "MODEL_INFERENCE", false, evidence));
        result.itemTransfers()
                .forEach(value -> insertCandidate(
                        job, chunk, "ITEM_OWNERSHIP", value, "LOW", "MODEL_INFERENCE", false, evidence));
        if (Mode.DEEP.name().equals(job.mode())) {
            result.knowledgeTransfers()
                    .forEach(value -> insertCandidate(
                            job, chunk, "CHARACTER_KNOWLEDGE", value, "LOW", "MODEL_INFERENCE", false, evidence));
        }
    }

    private void aggregate(JobRow job, String phase, String candidateType, String instruction) {
        transition(job.id(), phase);
        String sourceTypes =
                switch (candidateType) {
                    case "OUTLINE", "PROJECT_OVERVIEW", "FORESHADOW", "SKILL" -> "'CHAPTER_SUMMARY','EVENT'";
                    case "ENTITY_RESOLUTION" -> "'CHARACTER','WORLDBOOK','ITEM_OWNERSHIP'";
                    default -> "'CHAPTER_SUMMARY'";
                };
        List<String> source = jdbc.queryForList(
                "SELECT content FROM project_reconstruction_candidate WHERE job_id=? AND candidate_type IN ("
                        + sourceTypes + ") ORDER BY created_at LIMIT 400",
                String.class,
                job.id());
        if (source.isEmpty()) {
            step(job.id(), phase, "COMPLETED", 0, 0, "No source candidates for this phase");
            return;
        }
        String context = boundedJoin(source, 100_000);
        checkBudget(job.id());
        ExtractionResult result =
                extractor.extract(job.projectId(), job.ownerId(), new AgentInput(instruction, context));
        List<String> outputs =
                switch (candidateType) {
                    case "OUTLINE", "PROJECT_OVERVIEW" -> List.of(result.summary());
                    case "ENTITY_RESOLUTION" -> concat(result.characterChanges(), result.candidateFacts());
                    case "FORESHADOW" -> concat(result.events(), result.candidateFacts());
                    case "SKILL" -> result.candidateFacts();
                    default -> List.of(result.summary());
                };
        if (outputs.stream().allMatch(value -> value == null || value.isBlank())
                && result.summary() != null
                && !result.summary().isBlank()) {
            outputs = List.of(result.summary());
        }
        for (String output : outputs) {
            if (output == null || output.isBlank()) continue;
            insertGlobalCandidate(job, candidateType, output);
        }
        refreshUsage(job.id());
        step(job.id(), phase, "COMPLETED", source.size(), source.size(), "Candidate aggregation completed");
    }

    private void insertCandidate(
            JobRow job,
            ChunkRef chunk,
            String type,
            String content,
            String confidence,
            String inferenceType,
            boolean safe,
            Evidence evidence) {
        if (content == null || content.isBlank()) return;
        jdbc.update(
                "INSERT INTO project_reconstruction_candidate(id,job_id,project_id,chapter_id,chunk_id,candidate_type,natural_key,content,status,confidence,inference_type,evidence_count,source_coverage,source_anchors,safe_to_apply,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?)",
                UUID.randomUUID(),
                job.id(),
                job.projectId(),
                chunk.chapterId(),
                chunk.id(),
                type,
                naturalKey(content),
                content.strip(),
                "CANDIDATE",
                confidence,
                inferenceType,
                1,
                BigDecimal.ONE.divide(BigDecimal.valueOf(Math.max(1, job.totalChapters())), 6, RoundingMode.HALF_UP),
                json(evidence),
                safe,
                timestamp(),
                timestamp());
    }

    private void insertGlobalCandidate(JobRow job, String type, String content) {
        jdbc.update(
                "INSERT INTO project_reconstruction_candidate(id,job_id,project_id,candidate_type,natural_key,content,status,confidence,inference_type,evidence_count,source_coverage,source_anchors,safe_to_apply,created_at,updated_at) VALUES (?,?,?,?,?,?,'CANDIDATE','MEDIUM','MODEL_INFERENCE',0,1,'[]'::jsonb,FALSE,?,?)",
                UUID.randomUUID(),
                job.id(),
                job.projectId(),
                type,
                naturalKey(content),
                content.strip(),
                timestamp(),
                timestamp());
    }

    private void validateCandidates(UUID jobId) {
        jdbc.update(
                "UPDATE project_reconstruction_candidate SET status='CONFLICT',updated_at=? WHERE job_id=? AND (content ILIKE '%NEEDS_REVIEW%' OR content ILIKE '%冲突%' OR content ILIKE '%不确定%')",
                timestamp(), jobId);
    }

    private void deriveForeshadowCandidates(JobRow job) {
        if (!job.includeForeshadowing()) return;
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_reconstruction_candidate WHERE job_id=? AND candidate_type='FORESHADOW'",
                Integer.class,
                job.id());
        if (existing != null && existing > 0) return;
        List<DerivedCandidate> sources = jdbc.query(
                """
                SELECT chapter_id,chunk_id,content,evidence_count,source_coverage,source_anchors
                FROM project_reconstruction_candidate
                WHERE job_id=? AND candidate_type IN ('EVENT','WORLDBOOK')
                  AND content ~ '(失踪|下落|不知|未知|隐瞒|异常|秘密|纸条|封闭|真正|仿制|残照|灯芯|青色灯火|铃)'
                ORDER BY evidence_count DESC,created_at
                LIMIT 30
                """,
                (rs, index) -> new DerivedCandidate(
                        rs.getObject("chapter_id", UUID.class),
                        rs.getObject("chunk_id", UUID.class),
                        rs.getString("content"),
                        rs.getInt("evidence_count"),
                        rs.getBigDecimal("source_coverage"),
                        rs.getString("source_anchors")),
                job.id());
        for (DerivedCandidate source : sources) {
            jdbc.update(
                    """
                    INSERT INTO project_reconstruction_candidate(
                        id,job_id,project_id,chapter_id,chunk_id,candidate_type,natural_key,content,status,
                        confidence,inference_type,evidence_count,source_coverage,source_anchors,safe_to_apply,
                        created_at,updated_at)
                    VALUES (?,?,?,?,?,'FORESHADOW',?,?,'CANDIDATE','LOW','MODEL_INFERENCE',?,?,CAST(? AS jsonb),FALSE,?,?)
                    """,
                    UUID.randomUUID(),
                    job.id(),
                    job.projectId(),
                    source.chapterId(),
                    source.chunkId(),
                    naturalKey(source.content()),
                    source.content(),
                    source.evidenceCount(),
                    source.sourceCoverage(),
                    source.sourceAnchors(),
                    timestamp(),
                    timestamp());
        }
    }

    private void finishValidation(UUID jobId, String finalStatus) {
        if ("COMPLETED".equals(finalStatus)) {
            jdbc.update(
                    "UPDATE book_reconstruction_job SET status='COMPLETED',current_step='COMPLETED',completed_at=?,updated_at=?,version=version+1 WHERE id=?",
                    timestamp(),
                    timestamp(),
                    jobId);
            return;
        }
        jdbc.update(
                "UPDATE book_reconstruction_job SET status='WAITING_REVIEW',current_step='WAITING_REVIEW',completed_at=NULL,updated_at=?,version=version+1 WHERE id=?",
                timestamp(),
                jobId);
    }

    private boolean control(JobRow job) {
        if (job == null) return true;
        if (job.cancelRequested()) {
            markCancelled(job.id(), job.projectId());
            return true;
        }
        if (job.pauseRequested()) {
            jdbc.update(
                    "UPDATE book_reconstruction_job SET status='PAUSED',paused_at=?,updated_at=?,version=version+1 WHERE id=?",
                    timestamp(),
                    timestamp(),
                    job.id());
            step(
                    job.id(),
                    job.currentStep(),
                    "PAUSED",
                    job.processedChunks(),
                    job.totalChunks(),
                    "Paused at a persisted checkpoint");
            return true;
        }
        return STOPPED.contains(job.status());
    }

    private void checkBudget(UUID jobId) {
        refreshUsage(jobId);
        JobRow job = job(jobId);
        if (job != null && job.maxBudget() != null && job.actualCost().compareTo(job.maxBudget()) >= 0) {
            jdbc.update(
                    "UPDATE book_reconstruction_job SET status='PAUSED_BUDGET',paused_at=?,updated_at=?,version=version+1 WHERE id=?",
                    timestamp(),
                    timestamp(),
                    jobId);
            step(
                    jobId,
                    job.currentStep(),
                    "PAUSED",
                    job.processedChunks(),
                    job.totalChunks(),
                    "Maximum analysis budget reached");
            throw new ControlledStop();
        }
    }

    private void refreshUsage(UUID jobId) {
        jdbc.update(
                """
                UPDATE book_reconstruction_job j SET
                    actual_input_tokens=u.input_tokens,
                    actual_output_tokens=u.output_tokens,
                    actual_reasoning_tokens=u.reasoning_tokens,
                    actual_cost=u.actual_cost,
                    retry_count=u.retries,
                    updated_at=?
                FROM (
                    SELECT COALESCE(SUM(prompt_tokens),0) input_tokens,
                           COALESCE(SUM(completion_tokens),0) output_tokens,
                           COALESCE(SUM(reasoning_tokens),0) reasoning_tokens,
                           COALESCE(SUM(actual_cost),0) actual_cost,
                           COALESCE(SUM(GREATEST(attempts-1,0)),0) retries
                    FROM usage_record WHERE reconstruction_job_id=?
                ) u WHERE j.id=?
                """,
                timestamp(),
                jobId,
                jobId);
    }

    private void updateProgress(UUID jobId) {
        jdbc.update(
                "UPDATE book_reconstruction_job SET processed_chunks=(SELECT COUNT(*) FROM book_analysis_chunk WHERE job_id=? AND status='COMPLETED'),failed_chapters=(SELECT COUNT(DISTINCT chapter_id) FROM book_analysis_chunk WHERE job_id=? AND status='FAILED'),updated_at=? WHERE id=?",
                jobId,
                jobId,
                timestamp(),
                jobId);
    }

    private void finishPartial(JobRow job) {
        updateProgress(job.id());
        jdbc.update(
                "UPDATE book_reconstruction_job SET status='PARTIAL',current_step='CHAPTER_ANALYSIS',error_code='CHUNKS_FAILED',error_message='Some chapter chunks failed; retry them before global reconstruction',updated_at=?,version=version+1 WHERE id=?",
                timestamp(),
                job.id());
        jdbc.update(
                "UPDATE novel_project SET reconstruction_status='PARTIAL',updated_at=? WHERE id=?",
                timestamp(),
                job.projectId());
    }

    private void completeIfReviewed(UUID jobId, UUID projectId) {
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM project_reconstruction_candidate WHERE job_id=? AND status IN ('CANDIDATE','CONFLICT')",
                Integer.class,
                jobId);
        if (pending != null && pending == 0) {
            jdbc.update(
                    "UPDATE book_reconstruction_job SET status='COMPLETED',current_step='COMPLETED',completed_at=?,updated_at=?,version=version+1 WHERE id=?",
                    timestamp(),
                    timestamp(),
                    jobId);
            jdbc.update(
                    "UPDATE novel_project SET reconstruction_status='READY',updated_at=? WHERE id=?",
                    timestamp(),
                    projectId);
        } else {
            jdbc.update(
                    "UPDATE book_reconstruction_job SET status='WAITING_REVIEW',current_step='WAITING_REVIEW',updated_at=? WHERE id=?",
                    timestamp(),
                    jobId);
        }
    }

    private void markCancelled(UUID jobId, UUID projectId) {
        jdbc.update(
                "UPDATE book_reconstruction_job SET status='CANCELLED',current_step='CANCELLED',completed_at=?,updated_at=?,version=version+1 WHERE id=?",
                timestamp(),
                timestamp(),
                jobId);
        jdbc.update(
                "UPDATE novel_project SET reconstruction_status='PARTIAL',updated_at=? WHERE id=?",
                timestamp(),
                projectId);
    }

    private void transition(UUID jobId, String status) {
        jdbc.update(
                "UPDATE book_reconstruction_job SET status=?,current_step=?,updated_at=? WHERE id=?",
                status,
                status,
                timestamp(),
                jobId);
        step(jobId, status, "STARTED", 0, 0, null);
    }

    private void step(UUID jobId, String name, String status, int processed, int total, String summary) {
        jdbc.update(
                "INSERT INTO book_reconstruction_step(id,job_id,step_name,status,processed_units,total_units,summary,created_at) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                jobId,
                name,
                status,
                processed,
                total,
                summary,
                timestamp());
    }

    private ImportSource source(UUID projectId, UUID ownerId) {
        ImportSource value = jdbc.query(
                "SELECT id,total_characters,total_chapters FROM book_import_job WHERE project_id=? AND owner_id=? AND status='COMPLETED' ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? new ImportSource(rs.getObject(1, UUID.class), rs.getLong(2), rs.getInt(3)) : null,
                projectId,
                ownerId);
        if (value == null)
            throw new ConflictException("RECONSTRUCTION_REQUIRES_TXT_IMPORT", "A completed TXT import is required");
        return value;
    }

    private JobRow requireLatest(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        JobRow value = latest(projectId, ownerId);
        if (value == null) throw new NotFoundException("RECONSTRUCTION_NOT_FOUND", "Reconstruction job was not found");
        return value;
    }

    private JobRow latest(UUID projectId, UUID ownerId) {
        return jdbc.query(
                "SELECT * FROM book_reconstruction_job WHERE project_id=? AND owner_id=? ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? row(rs) : null,
                projectId,
                ownerId);
    }

    private JobRow requireJob(UUID id, UUID ownerId) {
        JobRow value = jdbc.query(
                "SELECT * FROM book_reconstruction_job WHERE id=? AND owner_id=?",
                rs -> rs.next() ? row(rs) : null,
                id,
                ownerId);
        if (value == null) throw new NotFoundException("RECONSTRUCTION_NOT_FOUND", "Reconstruction job was not found");
        return value;
    }

    private JobRow job(UUID id) {
        return jdbc.query("SELECT * FROM book_reconstruction_job WHERE id=?", rs -> rs.next() ? row(rs) : null, id);
    }

    private boolean hasActive(UUID projectId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_reconstruction_job WHERE project_id=? AND status IN ('QUEUED','PREPROCESSING','CHAPTER_ANALYSIS','VOLUME_AGGREGATION','ENTITY_RESOLUTION','GLOBAL_RECONSTRUCTION','FORESHADOW_ANALYSIS','SKILL_DISTILLATION','VALIDATING','APPLYING','PAUSED','PAUSED_BUDGET')",
                Integer.class,
                projectId);
        return count != null && count > 0;
    }

    private Counts counts(UUID jobId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*),COUNT(*) FILTER (WHERE status='CANDIDATE'),COUNT(*) FILTER (WHERE status='CONFLICT'),COUNT(*) FILTER (WHERE status IN ('ACCEPTED','APPLIED')),COUNT(*) FILTER (WHERE status='REJECTED') FROM project_reconstruction_candidate WHERE job_id=?",
                (rs, index) -> new Counts(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)),
                jobId);
    }

    private JobView view(JobRow row, Counts counts) {
        double progress = progress(row);
        return new JobView(
                row.id(),
                row.projectId(),
                Mode.valueOf(row.mode()),
                row.status(),
                row.currentStep(),
                row.totalChapters(),
                row.totalChunks(),
                row.processedChunks(),
                row.failedChapters(),
                progress,
                row.estimatedCalls(),
                row.estimatedInputTokens(),
                row.estimatedOutputTokens(),
                row.estimatedCostMin(),
                row.estimatedCostMax(),
                row.currency(),
                row.maxBudget(),
                row.actualInputTokens(),
                row.actualOutputTokens(),
                row.actualReasoningTokens(),
                row.actualCost(),
                row.retryCount(),
                counts.total(),
                counts.pending(),
                counts.conflicts(),
                counts.accepted(),
                counts.rejected(),
                row.errorCode(),
                row.errorMessage(),
                row.startedAt(),
                row.completedAt());
    }

    private double progress(JobRow row) {
        double chunks = row.totalChunks() == 0 ? 0 : Math.min(1, (double) row.processedChunks() / row.totalChunks());
        return switch (row.status()) {
            case "QUEUED" -> 0.01;
            case "PREPROCESSING" -> 0.03;
            case "CHAPTER_ANALYSIS" -> 0.05 + chunks * 0.70;
            case "VOLUME_AGGREGATION" -> 0.78;
            case "ENTITY_RESOLUTION" -> 0.84;
            case "GLOBAL_RECONSTRUCTION" -> 0.89;
            case "FORESHADOW_ANALYSIS" -> 0.93;
            case "SKILL_DISTILLATION" -> 0.96;
            case "VALIDATING" -> 0.98;
            case "APPLYING" -> 0.99;
            case "WAITING_REVIEW", "COMPLETED" -> 1.0;
            default -> Math.min(0.99, Math.max(chunks * 0.75, 0));
        };
    }

    private void materializeAwaitingCharacters() {
        List<JobRow> jobs = jdbc.query(
                """
                SELECT j.* FROM book_reconstruction_job j
                WHERE j.status IN ('WAITING_REVIEW','COMPLETED')
                  AND EXISTS (
                    SELECT 1 FROM project_reconstruction_candidate c
                    WHERE c.job_id=j.id AND c.candidate_type IN ('CHARACTER','ENTITY_RESOLUTION')
                      AND c.status IN ('CANDIDATE','ACCEPTED','CONFLICT')
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM book_reconstruction_step s
                    WHERE s.job_id=j.id AND s.step_name='CHARACTER_MATERIALIZATION'
                  )
                ORDER BY j.updated_at
                LIMIT 20
                """,
                (rs, index) -> row(rs));
        for (JobRow job : jobs) {
            try {
                validateCandidates(job.id());
                candidatePolicy.classify(job.id(), job.projectId());
                characterMaterializer.materialize(job.id(), job.projectId(), job.ownerId());
                candidatePolicy.classify(job.id(), job.projectId());
            } catch (RuntimeException exception) {
                jdbc.update(
                        "UPDATE book_reconstruction_job SET error_code='CHARACTER_MATERIALIZATION_FAILED',error_message=?,updated_at=? WHERE id=?",
                        safeMessage(exception),
                        timestamp(),
                        job.id());
            }
        }
    }

    private void materializeAwaitingProjectAssets() {
        List<JobRow> jobs = jdbc.query(
                """
                SELECT j.* FROM book_reconstruction_job j
                WHERE j.status IN ('WAITING_REVIEW','COMPLETED')
                  AND EXISTS (
                    SELECT 1 FROM project_reconstruction_candidate c
                    WHERE c.job_id=j.id AND c.candidate_type IN ('WORLDBOOK','OUTLINE','PROJECT_OVERVIEW')
                      AND c.status IN ('CANDIDATE','ACCEPTED','CONFLICT')
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM book_reconstruction_step s
                    WHERE s.job_id=j.id AND s.step_name='PROJECT_ASSET_MATERIALIZATION'
                  )
                ORDER BY j.updated_at
                LIMIT 20
                """,
                (rs, index) -> row(rs));
        for (JobRow job : jobs) {
            try {
                validateCandidates(job.id());
                candidatePolicy.classify(job.id(), job.projectId());
                projectAssetMaterializer.materialize(job.id(), job.projectId(), job.ownerId());
                jdbc.update(
                        "UPDATE book_reconstruction_job SET error_code=NULL,error_message=NULL,updated_at=? WHERE id=? AND error_code='PROJECT_ASSET_MATERIALIZATION_FAILED'",
                        timestamp(),
                        job.id());
            } catch (RuntimeException exception) {
                jdbc.update(
                        "UPDATE book_reconstruction_job SET error_code='PROJECT_ASSET_MATERIALIZATION_FAILED',error_message=?,updated_at=? WHERE id=?",
                        safeMessage(exception),
                        timestamp(),
                        job.id());
            }
        }
    }

    private JobRow row(ResultSet rs) throws SQLException {
        return new JobRow(
                rs.getObject("id", UUID.class),
                rs.getObject("import_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("owner_id", UUID.class),
                rs.getString("mode"),
                rs.getString("status"),
                rs.getString("current_step"),
                rs.getBoolean("include_skill_distillation"),
                rs.getBoolean("include_foreshadowing"),
                rs.getInt("total_chapters"),
                rs.getInt("total_chunks"),
                rs.getInt("processed_chunks"),
                rs.getInt("failed_chapters"),
                rs.getInt("estimated_calls"),
                rs.getLong("estimated_input_tokens"),
                rs.getLong("estimated_output_tokens"),
                rs.getBigDecimal("estimated_cost_min"),
                rs.getBigDecimal("estimated_cost_max"),
                rs.getString("estimate_currency"),
                rs.getBigDecimal("max_budget"),
                rs.getLong("actual_input_tokens"),
                rs.getLong("actual_output_tokens"),
                rs.getLong("actual_reasoning_tokens"),
                rs.getBigDecimal("actual_cost"),
                rs.getInt("retry_count"),
                rs.getBoolean("cancel_requested"),
                rs.getBoolean("pause_requested"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getTimestamp("started_at") == null
                        ? null
                        : rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at") == null
                        ? null
                        : rs.getTimestamp("completed_at").toInstant());
    }

    private CandidateView candidate(ResultSet rs) throws SQLException {
        return new CandidateView(
                rs.getObject("id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getString("candidate_type"),
                rs.getString("content"),
                rs.getString("status"),
                rs.getString("confidence"),
                rs.getString("inference_type"),
                rs.getInt("evidence_count"),
                rs.getBigDecimal("source_coverage"),
                rs.getString("source_anchors"),
                rs.getBoolean("safe_to_apply"),
                rs.getString("suggested_action"),
                rs.getObject("target_entity_id", UUID.class),
                rs.getString("subject_name"),
                rs.getString("policy_reason"),
                rs.getString("character_importance"),
                rs.getBoolean("retrieval_eligible"),
                rs.getTimestamp("revoked_at") == null
                        ? null
                        : rs.getTimestamp("revoked_at").toInstant(),
                rs.getString("revocation_reason"),
                rs.getTimestamp("created_at").toInstant());
    }

    private ChunkRef chunk(ResultSet rs) throws SQLException {
        return new ChunkRef(
                rs.getObject("id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getInt("sequence_no"),
                rs.getInt("chapter_index"),
                rs.getLong("start_offset"),
                rs.getLong("end_offset"),
                rs.getString("text_hash"));
    }

    private Reader chapterReader(UUID chapterId) {
        String content = jdbc.query(
                "SELECT cv.content FROM chapter c JOIN chapter_version cv ON cv.chapter_id=c.id AND cv.version_no=c.current_version_no WHERE c.id=?",
                rs -> rs.next() ? rs.getString(1) : null,
                chapterId);
        if (content == null) throw new IllegalStateException("Imported chapter content was not found");
        return new StringReader(content);
    }

    private String readChunk(UUID chapterId, long start, long end) {
        try (Reader reader = chapterReader(chapterId)) {
            long remainingSkip = start;
            while (remainingSkip > 0) {
                long skipped = reader.skip(remainingSkip);
                if (skipped <= 0) throw new IOException("Cannot seek chapter content");
                remainingSkip -= skipped;
            }
            int length = Math.toIntExact(end - start);
            char[] buffer = new char[length];
            int offset = 0;
            while (offset < length) {
                int count = reader.read(buffer, offset, length - offset);
                if (count < 0) break;
                offset += count;
            }
            return new String(buffer, 0, offset);
        } catch (IOException exception) {
            throw new IllegalStateException("Chapter chunk could not be read", exception);
        }
    }

    public static List<String> chunks(Reader source, int limit) throws IOException {
        List<String> result = new ArrayList<>();
        BufferedReader reader = source instanceof BufferedReader value ? value : new BufferedReader(source, 32 * 1024);
        StringBuilder current = new StringBuilder(limit);
        String line;
        while ((line = reader.readLine()) != null) {
            String segment = line + '\n';
            int cursor = 0;
            while (cursor < segment.length()) {
                int take = Math.min(limit - current.length(), segment.length() - cursor);
                current.append(segment, cursor, cursor + take);
                cursor += take;
                if (current.length() == limit) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            }
            if (current.length() >= limit * 3 / 4) {
                result.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private String chapterInstruction(Mode mode) {
        return "Analyze this single imported chapter chunk for reverse project reconstruction. Return only evidence-limited summary, events, candidate facts, character changes, item transfers and knowledge transfers. Do not rewrite source text, confirm canon, or invent future plot. Mode="
                + mode.name();
    }

    private String boundedJoin(List<String> values, int maxChars) {
        StringBuilder result = new StringBuilder(Math.min(maxChars, 32_000));
        for (String value : values) {
            if (result.length() >= maxChars) break;
            int remaining = maxChars - result.length();
            result.append(value, 0, Math.min(value.length(), remaining)).append('\n');
        }
        return result.toString();
    }

    private List<String> concat(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private String naturalKey(String value) {
        String normalized = value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(240, normalized.length()));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String json(Evidence evidence) {
        return json.writeValueAsString(List.of(evidence));
    }

    private TokenUsage tokenUsage(long input, long output) {
        return new TokenUsage(safeInt(input), safeInt(output), 0, 0, 0);
    }

    private int safeInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
    }

    private int failedChunks(UUID jobId) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_analysis_chunk WHERE job_id=? AND status='FAILED'", Integer.class, jobId);
        return value == null ? 0 : value;
    }

    private int completedChunks(UUID jobId) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_analysis_chunk WHERE job_id=? AND status='COMPLETED'", Integer.class, jobId);
        return value == null ? 0 : value;
    }

    private int totalChunks(UUID jobId) {
        Integer value =
                jdbc.queryForObject("SELECT COUNT(*) FROM book_analysis_chunk WHERE job_id=?", Integer.class, jobId);
        return value == null ? 0 : value;
    }

    private Timestamp timestamp() {
        return timestamp(clock.instant());
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return value.substring(0, Math.min(500, value.length()));
    }

    public enum Mode {
        QUICK(16_000, 500, 600),
        STANDARD(12_000, 800, 1_000),
        DEEP(8_000, 1_200, 1_600);
        private final int chunkCharacters;
        private final int promptOverhead;
        private final int outputTokens;

        Mode(int chunkCharacters, int promptOverhead, int outputTokens) {
            this.chunkCharacters = chunkCharacters;
            this.promptOverhead = promptOverhead;
            this.outputTokens = outputTokens;
        }

        public int chunkCharacters() {
            return chunkCharacters;
        }

        public int promptOverhead() {
            return promptOverhead;
        }

        public int outputTokens() {
            return outputTokens;
        }
    }

    public record StartRequest(
            Mode mode, boolean includeSkillDistillation, boolean includeForeshadowing, BigDecimal maxBudget) {}

    public record Estimate(
            Mode mode,
            int chapters,
            int chunks,
            int estimatedCalls,
            long estimatedInputTokens,
            long estimatedOutputTokens,
            BigDecimal estimatedCostMin,
            BigDecimal estimatedCostMax,
            String currency,
            String model,
            boolean unpriced) {}

    public record CandidateView(
            UUID id,
            UUID chapterId,
            String candidateType,
            String content,
            String status,
            String confidence,
            String inferenceType,
            int evidenceCount,
            BigDecimal sourceCoverage,
            String sourceAnchors,
            boolean safeToApply,
            String suggestedAction,
            UUID targetEntityId,
            String subjectName,
            String policyReason,
            String characterImportance,
            boolean retrievalEligible,
            Instant revokedAt,
            String revocationReason,
            Instant createdAt) {}

    public record JobView(
            UUID id,
            UUID projectId,
            Mode mode,
            String status,
            String currentStep,
            int totalChapters,
            int totalChunks,
            int processedChunks,
            int failedChapters,
            double progress,
            int estimatedCalls,
            long estimatedInputTokens,
            long estimatedOutputTokens,
            BigDecimal estimatedCostMin,
            BigDecimal estimatedCostMax,
            String currency,
            BigDecimal maxBudget,
            long actualInputTokens,
            long actualOutputTokens,
            long actualReasoningTokens,
            BigDecimal actualCost,
            int retryCount,
            int candidateCount,
            int pendingCandidates,
            int conflicts,
            int acceptedCandidates,
            int rejectedCandidates,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt) {
        static JobView notAnalyzed(UUID projectId, int chapters) {
            return new JobView(
                    null,
                    projectId,
                    Mode.STANDARD,
                    "NOT_ANALYZED",
                    "NOT_ANALYZED",
                    chapters,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,
                    0,
                    BigDecimal.ZERO,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    null,
                    null,
                    null);
        }
    }

    private record ImportSource(UUID importId, long totalCharacters, int totalChapters) {}

    private record ChapterRef(UUID id, int index) {}

    private record ChunkRef(
            UUID id,
            UUID chapterId,
            int sequence,
            int chapterIndex,
            long startOffset,
            long endOffset,
            String textHash) {}

    private record Evidence(UUID chapterId, int chapterIndex, long startOffset, long endOffset, String contentHash) {}

    private record Counts(int total, int pending, int conflicts, int accepted, int rejected) {}

    private record DerivedCandidate(
            UUID chapterId,
            UUID chunkId,
            String content,
            int evidenceCount,
            BigDecimal sourceCoverage,
            String sourceAnchors) {}

    private record JobRow(
            UUID id,
            UUID importId,
            UUID projectId,
            UUID ownerId,
            String mode,
            String status,
            String currentStep,
            boolean includeSkill,
            boolean includeForeshadowing,
            int totalChapters,
            int totalChunks,
            int processedChunks,
            int failedChapters,
            int estimatedCalls,
            long estimatedInputTokens,
            long estimatedOutputTokens,
            BigDecimal estimatedCostMin,
            BigDecimal estimatedCostMax,
            String currency,
            BigDecimal maxBudget,
            long actualInputTokens,
            long actualOutputTokens,
            long actualReasoningTokens,
            BigDecimal actualCost,
            int retryCount,
            boolean cancelRequested,
            boolean pauseRequested,
            String errorCode,
            String errorMessage,
            Instant startedAt,
            Instant completedAt) {}

    private static final class ControlledStop extends RuntimeException {}
}
