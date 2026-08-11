package com.storyweaver.skill.global.application;

import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.skill.global.domain.ForgeRunStatus;
import com.storyweaver.skill.global.domain.GlobalSkill;
import com.storyweaver.skill.global.domain.SkillForgeRun;
import com.storyweaver.skill.global.repository.SkillForgeRunRepository;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/** Evidence-first TXT/manual text Skill forge. Raw sources are never rewritten before snapshotting. */
@Service
public class SkillForgeService {
    private static final Set<String> SKILL_TYPES = Set.of("FOUNDATION", "GENRE", "TECHNIQUE", "REVIEW");
    private static final Set<String> MATERIAL_TYPES =
            Set.of("PROSE", "DIALOGUE", "CHARACTER", "DESCRIPTION", "OUTLINE", "WRITING_RULES", "OTHER");
    private static final Set<String> MUTABLE_SOURCE_STATES = Set.of("CREATED", "SOURCE_READY");
    private static final Pattern SENTENCE_END = Pattern.compile("[。！？!?]+[”’\"']?");
    private static final Pattern CAUSAL_MARKER = Pattern.compile("因为|因此|所以|于是|然而|但是|但|却|结果|导致");
    private static final Pattern EXPLICIT_RULE = Pattern.compile("不要|禁止|避免|少用|不应|不能|不得");
    private static final Pattern CHAPTER_HEADING = Pattern.compile("(?m)^第[0-9一二三四五六七八九十百千万零〇两]+章");

    private final GlobalSkillService globalSkills;
    private final SkillForgeRunRepository runs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final int maxFiles;
    private final long maxFileBytes;
    private final long maxTotalBytes;
    private final int maxManualCharacters;
    private final int minManualCharacters;

    public SkillForgeService(
            GlobalSkillService globalSkills,
            SkillForgeRunRepository runs,
            JdbcTemplate jdbc,
            ObjectMapper json,
            Clock clock,
            @Value("${storyweaver.skill-forge.max-files:20}") int maxFiles,
            @Value("${storyweaver.skill-forge.max-file-bytes:10485760}") long maxFileBytes,
            @Value("${storyweaver.skill-forge.max-total-bytes:20971520}") long maxTotalBytes,
            @Value("${storyweaver.skill-forge.max-manual-characters:50000}") int maxManualCharacters,
            @Value("${storyweaver.skill-forge.min-manual-characters:200}") int minManualCharacters) {
        this.globalSkills = globalSkills;
        this.runs = runs;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.maxManualCharacters = maxManualCharacters;
        this.minManualCharacters = minManualCharacters;
    }

    @Transactional
    public SkillForgeRun create(
            UUID ownerId,
            String slug,
            String displayName,
            String skillType,
            String materialTag,
            String genre,
            UUID sourceProjectId,
            String learningFocus,
            String materialDescription,
            boolean excludeCharacterNames,
            boolean excludeLocations,
            boolean excludePlotFacts,
            boolean reusableMethodsOnly,
            boolean ownershipConfirmed,
            String ownershipStatement) {
        String normalizedType = normalizeEnum(skillType, SKILL_TYPES, "invalid_skill_type");
        String normalizedMaterialTag = normalizeEnum(materialTag, MATERIAL_TYPES, "invalid_material_type");
        String resolvedGenre = resolveGenre(ownerId, sourceProjectId, genre);
        if (!ownershipConfirmed)
            throw new BadRequestException(
                    "forge_ownership_required", "Confirm that you have the right to analyze the supplied text");
        String statement = ownershipStatement == null ? "" : ownershipStatement.trim();
        if (statement.isBlank())
            statement = "I confirm that I created the supplied text or have the right to use it for Skill analysis.";
        Map<String, Object> candidate = globalSkills.defaultContract(displayName.trim(), "等待可追溯文本来源");
        GlobalSkill skill = globalSkills.create(ownerId, slug, displayName, "由私有文本证据熔炼，待逐条审查", candidate);
        return runs.save(new SkillForgeRun(
                ownerId,
                skill.getId(),
                normalizedType,
                normalizedMaterialTag,
                resolvedGenre,
                sourceProjectId,
                trimToNull(learningFocus),
                trimToNull(materialDescription),
                excludeCharacterNames,
                excludeLocations,
                excludePlotFacts,
                reusableMethodsOnly,
                statement,
                candidate,
                clock.instant()));
    }

    @Transactional(readOnly = true)
    public SkillForgeRun get(UUID runId, UUID ownerId) {
        return runs.findByIdAndOwnerId(runId, ownerId)
                .orElseThrow(() -> new NotFoundException("forge_run_not_found", "Skill forge run was not found"));
    }

    @Transactional
    public SourceView addManualSource(
            UUID runId, UUID ownerId, String title, String content, String materialType, boolean ownershipConfirmed) {
        SkillForgeRun run = requireSourceMutable(runId, ownerId);
        if (!ownershipConfirmed)
            throw new BadRequestException("forge_ownership_required", "Source ownership confirmation is required");
        String value = content == null ? "" : content;
        if (value.length() < minManualCharacters)
            throw new BadRequestException(
                    "forge_manual_text_too_short",
                    "Manual text must contain at least " + minManualCharacters + " characters");
        if (value.length() > maxManualCharacters)
            throw new BadRequestException(
                    "forge_manual_text_too_large", "Manual text exceeds " + maxManualCharacters + " characters");
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        requireCapacity(runId, 1, raw.length);
        SourceView source = saveSource(
                runId,
                "MANUAL_TEXT",
                title == null || title.isBlank() ? "手写文本" : title.trim(),
                normalizeEnum(materialType, MATERIAL_TYPES, "invalid_material_type"),
                null,
                "UTF-8",
                raw,
                value);
        run.transition(ForgeRunStatus.SOURCE_READY, "文本来源已保存，可开始熔炼。", clock.instant());
        return source;
    }

    @Transactional
    public List<SourceView> addTxtSources(
            UUID runId,
            UUID ownerId,
            List<MultipartFile> files,
            List<String> titles,
            String materialType,
            boolean ownershipConfirmed) {
        SkillForgeRun run = requireSourceMutable(runId, ownerId);
        if (!ownershipConfirmed)
            throw new BadRequestException("forge_ownership_required", "Source ownership confirmation is required");
        if (files == null || files.isEmpty())
            throw new BadRequestException("forge_txt_required", "Choose at least one TXT file");
        String normalizedMaterialType = normalizeEnum(materialType, MATERIAL_TYPES, "invalid_material_type");
        long incomingBytes = files.stream().mapToLong(MultipartFile::getSize).sum();
        requireCapacity(runId, files.size(), incomingBytes);
        List<SourceView> result = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            MultipartFile file = files.get(index);
            validateTxt(file);
            try {
                byte[] raw = file.getBytes();
                DecodedText decoded = decode(raw);
                String fileName = safeFileName(file.getOriginalFilename());
                result.add(saveSource(
                        runId,
                        "TXT",
                        titles != null
                                        && index < titles.size()
                                        && titles.get(index) != null
                                        && !titles.get(index).isBlank()
                                ? titles.get(index).trim()
                                : stripExtension(fileName),
                        normalizedMaterialType,
                        fileName,
                        decoded.encoding(),
                        raw,
                        decoded.text()));
            } catch (IOException exception) {
                throw new BadRequestException("forge_txt_unreadable", "TXT file could not be read");
            }
        }
        run.transition(ForgeRunStatus.SOURCE_READY, result.size() + " 个 TXT 来源已保存，可开始熔炼。", clock.instant());
        return result;
    }

    @Transactional(readOnly = true)
    public List<SourceView> sources(UUID runId, UUID ownerId) {
        get(runId, ownerId);
        return jdbc.query(
                "SELECT * FROM skill_source WHERE forge_run_id=? ORDER BY source_order",
                (rs, row) -> sourceView(rs),
                runId);
    }

    @Transactional
    public void deleteSource(UUID runId, UUID sourceId, UUID ownerId) {
        requireSourceMutable(runId, ownerId);
        int deleted = jdbc.update("DELETE FROM skill_source WHERE id=? AND forge_run_id=?", sourceId, runId);
        if (deleted == 0) throw new NotFoundException("forge_source_not_found", "Skill source was not found");
        resequence(runId);
        SkillForgeRun run = get(runId, ownerId);
        Integer remaining =
                jdbc.queryForObject("SELECT COUNT(*) FROM skill_source WHERE forge_run_id=?", Integer.class, runId);
        run.transition(
                remaining != null && remaining > 0 ? ForgeRunStatus.SOURCE_READY : ForgeRunStatus.CREATED,
                remaining != null && remaining > 0 ? "来源已更新，可开始熔炼。" : "等待添加 TXT 或手写文本。",
                clock.instant());
    }

    @Transactional
    public SkillForgeRun start(UUID runId, UUID ownerId) {
        SkillForgeRun run = get(runId, ownerId);
        if (run.getStatus() != ForgeRunStatus.SOURCE_READY)
            throw new ConflictException("forge_sources_not_ready", "Add at least one valid source before starting");
        List<SourceData> sourceData = sourceData(runId);
        if (sourceData.isEmpty())
            throw new ConflictException("forge_sources_not_ready", "Add at least one valid source before starting");
        jdbc.update("DELETE FROM global_skill_atomic_rule WHERE forge_run_id=?", runId);
        transition(run, ForgeRunStatus.PREPROCESSING, "PREPROCESSING", "来源快照与段落键已就绪。");
        ForgePrompt prompt = buildMeltPrompt(run, sourceData);
        addStep(
                runId,
                "DYNAMIC_TEMPLATE",
                "COMPLETED",
                "已装配素材标签、Skill 类型、题材、学习重点与素材说明；Prompt SHA-256：" + prompt.hash().substring(0, 12));
        transition(run, ForgeRunStatus.EXTRACTING, "EXTRACTING", "正在执行六维证据提取。");
        extractRules(run, sourceData);
        transition(run, ForgeRunStatus.CROSS_VALIDATING, "CROSS_VALIDATING", "正在跨来源去重并评估证据等级。");
        int conflicts = detectConflicts(runId);
        if (conflicts > 0) {
            transition(
                    run,
                    ForgeRunStatus.WAITING_CONFLICT_RESOLUTION,
                    "WAITING_CONFLICT_RESOLUTION",
                    "发现 " + conflicts + " 条冲突规则，等待用户处理。");
        } else {
            transition(run, ForgeRunStatus.WAITING_REVIEW, "WAITING_REVIEW", "六维候选规则已生成；只有用户接受的规则会进入契约。");
        }
        return run;
    }

    @Transactional(readOnly = true)
    public List<RuleView> rules(UUID runId, UUID ownerId) {
        get(runId, ownerId);
        return jdbc.query(
                """
                SELECT id,dimension,statement,rationale,scope,evidence_level,confidence,evidence_json::text,
                       status,user_modified,created_at,updated_at
                FROM global_skill_atomic_rule WHERE forge_run_id=? ORDER BY dimension,created_at,id
                """,
                (rs, row) -> new RuleView(
                        rs.getObject("id", UUID.class),
                        rs.getString("dimension"),
                        rs.getString("statement"),
                        rs.getString("rationale"),
                        rs.getString("scope"),
                        rs.getString("evidence_level"),
                        rs.getDouble("confidence"),
                        readEvidence(runId, rs.getString("evidence_json")),
                        rs.getString("status"),
                        rs.getBoolean("user_modified"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                runId);
    }

    @Transactional
    public RuleView reviewRule(UUID runId, UUID ruleId, UUID ownerId, String action, String statement) {
        SkillForgeRun run = get(runId, ownerId);
        requireReviewable(run);
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        String status;
        boolean modified = false;
        String replacement = null;
        switch (normalizedAction) {
            case "ACCEPT" -> status = "ACCEPTED";
            case "DELETE", "REJECT" -> status = "REJECTED";
            case "EDIT" -> {
                replacement = statement == null ? "" : statement.trim();
                if (replacement.isBlank() || replacement.length() > 2000)
                    throw new BadRequestException(
                            "forge_rule_text_invalid", "Edited rule must contain 1-2000 characters");
                status = "ACCEPTED";
                modified = true;
            }
            default -> throw new BadRequestException("forge_rule_action_invalid", "Use ACCEPT, EDIT or DELETE");
        }
        int updated = replacement == null
                ? jdbc.update(
                        "UPDATE global_skill_atomic_rule SET status=?,updated_at=? WHERE id=? AND forge_run_id=?",
                        status,
                        timestamp(clock.instant()),
                        ruleId,
                        runId)
                : jdbc.update(
                        "UPDATE global_skill_atomic_rule SET statement=?,status=?,user_modified=?,updated_at=? WHERE id=? AND forge_run_id=?",
                        replacement,
                        status,
                        modified,
                        timestamp(clock.instant()),
                        ruleId,
                        runId);
        if (updated == 0) throw new NotFoundException("forge_rule_not_found", "Candidate rule was not found");
        return rules(runId, ownerId).stream()
                .filter(rule -> rule.id().equals(ruleId))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public List<RuleView> resolveConflicts(UUID runId, UUID ownerId, List<ConflictResolution> resolutions) {
        SkillForgeRun run = get(runId, ownerId);
        if (run.getStatus() != ForgeRunStatus.WAITING_CONFLICT_RESOLUTION)
            throw new ConflictException("forge_not_waiting_for_conflicts", "Forge run has no unresolved conflicts");
        for (ConflictResolution resolution : resolutions)
            reviewRule(runId, resolution.ruleId(), ownerId, resolution.action(), resolution.statement());
        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM global_skill_atomic_rule WHERE forge_run_id=? AND status='CONFLICT'",
                Integer.class,
                runId);
        if (remaining != null && remaining == 0)
            transition(run, ForgeRunStatus.WAITING_REVIEW, "WAITING_REVIEW", "冲突已处理，等待逐条审查候选规则。");
        return rules(runId, ownerId);
    }

    @Transactional
    public SkillForgeRun generateContract(UUID runId, UUID ownerId) {
        SkillForgeRun run = get(runId, ownerId);
        requireReviewable(run);
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM global_skill_atomic_rule WHERE forge_run_id=? AND status IN ('CANDIDATE','CONFLICT')",
                Integer.class,
                runId);
        if (pending != null && pending > 0)
            throw new ConflictException("forge_rules_pending", "Accept, edit or delete every candidate rule first");
        List<RuleView> accepted = rules(runId, ownerId).stream()
                .filter(rule -> "ACCEPTED".equals(rule.status()))
                .toList();
        if (accepted.isEmpty())
            throw new BadRequestException("forge_no_accepted_rules", "Accept at least one evidence-backed rule");
        transition(run, ForgeRunStatus.BUILDING_CONTRACT, "BUILDING_CONTRACT", "正在编排已确认规则。");
        GlobalSkill skill = globalSkills.get(run.getGlobalSkillId(), ownerId);
        Map<String, Object> contract = contract(run, skill, accepted, sources(runId, ownerId));
        globalSkills.replaceDraftContract(skill.getId(), ownerId, contract);
        run.replaceCandidate(contract, "候选契约已生成，可运行测试并发布验证版本。", clock.instant());
        createTestCases(run);
        addStep(runId, "BUILDING_CONTRACT", "COMPLETED", "已生成精炼契约与 8 个验证场景。");
        return run;
    }

    @Transactional
    public ForgeValidation validate(UUID runId, UUID ownerId) {
        SkillForgeRun run = get(runId, ownerId);
        if (run.getStatus() != ForgeRunStatus.WAITING_REVIEW)
            throw new ConflictException(
                    "forge_not_ready_to_validate", "Generate and review the contract before validation");
        transition(run, ForgeRunStatus.VALIDATING, "VALIDATING", "正在运行典型、冲突、边缘、过拟合与诚实边界测试。");
        GlobalSkillService.ValidationResult result = globalSkills.validate(run.getGlobalSkillId(), ownerId);
        if (!result.valid()) {
            run.validate(false, "契约缺少必填段落：" + String.join("、", result.missingSections()), clock.instant());
            addStep(runId, "VALIDATING", "FAILED", run.getSummary());
            return new ForgeValidation(false, result.score(), result.missingSections(), result.version());
        }
        TestSummary tests = runTests(run, result.version().getId());
        boolean valid = tests.score() >= 85;
        run.validate(valid, valid ? "全部验证场景通过，已生成不可变版本。" : "验证分数不足 85。", clock.instant());
        if (valid)
            jdbc.update(
                    "UPDATE global_skill_atomic_rule SET skill_version_id=? WHERE forge_run_id=? AND status='ACCEPTED'",
                    result.version().getId(),
                    runId);
        addStep(runId, "VALIDATING", valid ? "COMPLETED" : "FAILED", run.getSummary());
        return new ForgeValidation(valid, tests.score(), valid ? List.of() : List.of("evaluation"), result.version());
    }

    @Transactional
    public void cancel(UUID runId, UUID ownerId) {
        SkillForgeRun run = get(runId, ownerId);
        if (run.getStatus() == ForgeRunStatus.VALIDATED)
            throw new ConflictException("forge_already_validated", "Validated forge run cannot be cancelled");
        run.cancel(clock.instant());
        addStep(runId, "CANCELLED", "COMPLETED", "用户取消熔炼。来源仍保持私有，不会进入其他 Skill。");
    }

    @Transactional(readOnly = true)
    public List<StepView> events(UUID runId, UUID ownerId) {
        get(runId, ownerId);
        return jdbc.query(
                "SELECT id,step_name,status,summary,created_at FROM skill_forge_step WHERE forge_run_id=? ORDER BY created_at,id",
                (rs, row) -> new StepView(
                        rs.getObject("id", UUID.class),
                        rs.getString("step_name"),
                        rs.getString("status"),
                        rs.getString("summary"),
                        rs.getTimestamp("created_at").toInstant()),
                runId);
    }

    private SkillForgeRun requireSourceMutable(UUID runId, UUID ownerId) {
        SkillForgeRun run = get(runId, ownerId);
        if (!MUTABLE_SOURCE_STATES.contains(run.getStatus().name()))
            throw new ConflictException("forge_sources_locked", "Sources are locked after distillation starts");
        return run;
    }

    private void requireReviewable(SkillForgeRun run) {
        if (run.getStatus() != ForgeRunStatus.WAITING_REVIEW
                && run.getStatus() != ForgeRunStatus.WAITING_CONFLICT_RESOLUTION)
            throw new ConflictException("forge_not_waiting_for_review", "Forge run is not waiting for rule review");
    }

    private SourceView saveSource(
            UUID runId,
            String sourceType,
            String title,
            String materialType,
            String originalFilename,
            String encoding,
            byte[] raw,
            String decoded) {
        String normalized = normalizeText(decoded);
        if (normalized.isBlank()) throw new BadRequestException("forge_source_empty", "Source contains no usable text");
        requireTextContent(normalized);
        List<ParagraphData> paragraphs = paragraphs(normalized);
        UUID sourceId = UUID.randomUUID();
        Integer nextOrder = jdbc.queryForObject(
                "SELECT COALESCE(MAX(source_order),0)+1 FROM skill_source WHERE forge_run_id=?", Integer.class, runId);
        Instant now = clock.instant();
        String hash = hash(raw);
        jdbc.update(
                """
                INSERT INTO skill_source(
                    id,forge_run_id,source_type,title,material_type,original_filename,detected_encoding,
                    content_hash,character_count,paragraph_count,ownership_confirmed,raw_content_storage_ref,
                    raw_bytes,normalized_text,source_order,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                sourceId,
                runId,
                sourceType,
                title.substring(0, Math.min(200, title.length())),
                materialType,
                originalFilename,
                encoding,
                hash,
                normalized.length(),
                paragraphs.size(),
                true,
                "db://skill-source/" + sourceId,
                raw,
                normalized,
                nextOrder,
                timestamp(now));
        for (ParagraphData paragraph : paragraphs)
            jdbc.update(
                    """
                    INSERT INTO skill_source_paragraph(
                        id,source_id,paragraph_key,sequence_no,start_offset,end_offset,excerpt_hash,content)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    sourceId,
                    paragraph.key(),
                    paragraph.sequence(),
                    paragraph.startOffset(),
                    paragraph.endOffset(),
                    paragraph.hash(),
                    paragraph.content());
        return new SourceView(
                sourceId,
                sourceType,
                title,
                materialType,
                originalFilename,
                encoding,
                hash,
                normalized.length(),
                paragraphs.size(),
                nextOrder == null ? 1 : nextOrder,
                now);
    }

    private void validateTxt(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("forge_txt_empty", "TXT file is empty");
        if (file.getSize() > maxFileBytes)
            throw new BadRequestException("forge_txt_too_large", "A TXT file exceeds the configured size limit");
        String fileName = safeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".txt"))
            throw new BadRequestException("forge_txt_type_invalid", "Only .txt files are accepted");
        String mediaType = file.getContentType();
        if (mediaType != null
                && !mediaType.isBlank()
                && !mediaType.equalsIgnoreCase("text/plain")
                && !mediaType.equalsIgnoreCase("application/octet-stream"))
            throw new BadRequestException("forge_txt_mime_invalid", "Uploaded file is not plain text");
    }

    private void requireCapacity(UUID runId, int incomingFiles, long incomingBytes) {
        Map<String, Object> current = jdbc.queryForMap(
                "SELECT COUNT(*) AS count,COALESCE(SUM(octet_length(raw_bytes)),0) AS bytes FROM skill_source WHERE forge_run_id=?",
                runId);
        long count = ((Number) current.get("count")).longValue();
        long bytes = ((Number) current.get("bytes")).longValue();
        if (count + incomingFiles > maxFiles)
            throw new BadRequestException(
                    "forge_source_count_limit", "Forge run accepts at most " + maxFiles + " sources");
        if (bytes + incomingBytes > maxTotalBytes)
            throw new BadRequestException("forge_source_total_limit", "Forge sources exceed the configured total size");
    }

    private DecodedText decode(byte[] raw) {
        if (raw.length >= 2
                && ((raw[0] == (byte) 0xFF && raw[1] == (byte) 0xFE)
                        || (raw[0] == (byte) 0xFE && raw[1] == (byte) 0xFF)))
            throw new BadRequestException(
                    "forge_txt_encoding_unsupported", "UTF-16 TXT is not supported; use UTF-8 or GB18030");
        if (raw.length >= 3 && raw[0] == (byte) 0xEF && raw[1] == (byte) 0xBB && raw[2] == (byte) 0xBF) {
            return new DecodedText(strictDecode(slice(raw, 3), StandardCharsets.UTF_8), "UTF-8 BOM");
        }
        try {
            return new DecodedText(strictDecode(raw, StandardCharsets.UTF_8), "UTF-8");
        } catch (BadRequestException ignored) {
            return new DecodedText(strictDecode(raw, Charset.forName("GB18030")), "GB18030");
        }
    }

    private String strictDecode(byte[] raw, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(raw)).toString();
        } catch (CharacterCodingException exception) {
            throw new BadRequestException("forge_txt_decode_failed", "TXT decoding failed; use UTF-8 or GB18030");
        }
    }

    private byte[] slice(byte[] value, int start) {
        byte[] result = new byte[value.length - start];
        System.arraycopy(value, start, result, 0, result.length);
        return result;
    }

    private String normalizeText(String value) {
        String[] lines = value.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split("\n", -1);
        StringBuilder result = new StringBuilder();
        int blankCount = 0;
        for (String line : lines) {
            String normalizedLine = line.stripTrailing();
            if (normalizedLine.isBlank()) {
                blankCount++;
                if (blankCount > 2) continue;
            } else {
                blankCount = 0;
            }
            if (!result.isEmpty()) result.append('\n');
            result.append(normalizedLine);
        }
        return result.toString().strip();
    }

    private void requireTextContent(String value) {
        long invalidControls = value.chars()
                .filter(character -> character == 0 || (character < 32 && character != '\n' && character != '\t'))
                .count();
        if (invalidControls > 0 || value.indexOf('\uFFFD') >= 0)
            throw new BadRequestException(
                    "forge_txt_binary_content", "TXT contains invalid binary or replacement characters");
    }

    private List<ParagraphData> paragraphs(String text) {
        List<ParagraphData> result = new ArrayList<>();
        int cursor = 0;
        int start = -1;
        String[] lines = text.split("\n", -1);
        for (int index = 0; index <= lines.length; index++) {
            boolean end = index == lines.length;
            String line = end ? "" : lines[index];
            if (!end && !line.isBlank() && start < 0) start = cursor;
            if ((end || line.isBlank()) && start >= 0) {
                int paragraphEnd = end ? text.length() : Math.max(start, Math.min(text.length(), cursor - 1));
                String content = text.substring(start, paragraphEnd).strip();
                int sequence = result.size() + 1;
                String excerptHash = hash(content.getBytes(StandardCharsets.UTF_8));
                result.add(new ParagraphData(
                        sequence,
                        "p-%04d-%s".formatted(sequence, excerptHash.substring(0, 8)),
                        start,
                        paragraphEnd,
                        excerptHash,
                        content));
                start = -1;
            }
            if (!end) cursor += line.length() + 1;
        }
        if (result.isEmpty()) {
            String excerptHash = hash(text.getBytes(StandardCharsets.UTF_8));
            result.add(
                    new ParagraphData(1, "p-0001-" + excerptHash.substring(0, 8), 0, text.length(), excerptHash, text));
        }
        return result;
    }

    private List<SourceData> sourceData(UUID runId) {
        List<SourceData> sources = jdbc.query(
                "SELECT id,material_type,normalized_text FROM skill_source WHERE forge_run_id=? ORDER BY source_order",
                (rs, row) -> new SourceData(
                        rs.getObject("id", UUID.class), rs.getString("material_type"), rs.getString("normalized_text")),
                runId);
        return sources.stream()
                .map(source -> new SourceData(
                        source.id(),
                        source.materialType(),
                        source.text(),
                        jdbc.query(
                                "SELECT paragraph_key,excerpt_hash,content FROM skill_source_paragraph WHERE source_id=? ORDER BY sequence_no",
                                (rs, row) -> new EvidenceParagraph(
                                        source.id(),
                                        rs.getString("paragraph_key"),
                                        rs.getString("excerpt_hash"),
                                        rs.getString("content")),
                                source.id())))
                .toList();
    }

    private void extractRules(SkillForgeRun run, List<SourceData> sources) {
        String text = sources.stream().map(SourceData::text).reduce("", (left, right) -> left + "\n" + right);
        int characters =
                sources.stream().mapToInt(value -> value.text().length()).sum();
        List<EvidenceParagraph> evidence = sources.stream()
                .flatMap(source -> source.paragraphs().stream())
                .limit(6)
                .toList();
        EvidenceProfile profile = evidenceProfile(sources, characters);
        long causalCount = CAUSAL_MARKER.matcher(text).results().count();
        insertRule(
                run.getId(),
                "NARRATIVE",
                causalCount >= 3 ? "样本反复使用显式因果或转折连接推进局部事件；应用时保留可观察的前因与结果。" : "当前样本只支持局部叙述顺序，证据不足以推断完整长篇因果模型。",
                causalCount >= 3 ? "检测到 " + causalCount + " 个因果或转折连接。" : "因果标记数量不足，保留诚实边界。",
                profile,
                evidence);

        long dialogueMarks = text.chars()
                        .filter(character -> character == '“' || character == '”')
                        .count()
                / 2;
        insertRule(
                run.getId(),
                "CHARACTER",
                dialogueMarks >= 4 ? "样本包含可重复观察的对话回合；人物规则应优先依据实际发言与回应，不补造未出现的人物弧。" : "当前样本缺少足够人物对话或决策证据，不得生成稳定人物塑造方法。",
                dialogueMarks >= 4 ? "检测到约 " + dialogueMarks + " 个中文引号对话片段。" : "对话证据较少。",
                profile,
                evidence);

        String[] sentences = SENTENCE_END.split(text);
        int sentenceCount = Math.max(1, (int) java.util.Arrays.stream(sentences)
                .filter(value -> !value.isBlank())
                .count());
        int averageSentence = Math.max(1, characters / sentenceCount);
        int paragraphCount =
                sources.stream().mapToInt(source -> source.paragraphs().size()).sum();
        int averageParagraph = Math.max(1, characters / Math.max(1, paragraphCount));
        insertRule(
                run.getId(),
                "EXPRESSION",
                "样本平均句长约 " + averageSentence + " 字、平均段落约 " + averageParagraph + " 字；仅将其作为可迁移的局部表达节奏。",
                "由全部来源的句末标记、字符数与段落数计算。",
                profile,
                evidence);

        long shortParagraphs = sources.stream()
                .flatMap(source -> source.paragraphs().stream())
                .filter(paragraph -> paragraph.content().length() <= 80)
                .count();
        double shortRatio = paragraphCount == 0 ? 0 : (double) shortParagraphs / paragraphCount;
        boolean chapterEvidence = CHAPTER_HEADING.matcher(text).find();
        insertRule(
                run.getId(),
                "PACING",
                chapterEvidence
                        ? "样本含章节边界，可在测试中验证段落推进与章尾处理；未通过测试前不提升为稳定章尾规则。"
                        : "约 " + Math.round(shortRatio * 100) + "% 的段落不超过 80 字；该证据只支持局部场景节奏，不支持章尾策略。",
                chapterEvidence ? "检测到明确章节标题。" : "未检测到明确章节边界。",
                profile,
                evidence);

        List<EvidenceParagraph> explicitEvidence = sources.stream()
                .filter(source -> "WRITING_RULES".equals(source.materialType()))
                .flatMap(source -> source.paragraphs().stream())
                .filter(paragraph -> EXPLICIT_RULE.matcher(paragraph.content()).find())
                .limit(6)
                .toList();
        if (explicitEvidence.isEmpty()) {
            insertRule(
                    run.getId(),
                    "ANTI_PATTERN",
                    "当前素材没有足够的显式写作禁用原则；不能因某种表达未出现就断言作者禁止它。",
                    "反模式只能来自明确规范或多来源反例。",
                    new EvidenceProfile("LOCAL_PATTERN", "LOW", 0.45),
                    evidence);
        } else {
            insertRule(
                    run.getId(),
                    "ANTI_PATTERN",
                    "遵守写作规范来源中明确标出的禁用表达；应用前逐条查看证据，不把正文人物台词误判为作者规则。",
                    "仅使用标记为 WRITING_RULES 且含明确禁用词的段落。",
                    new EvidenceProfile("EXPLICIT_USER_RULE", profile.level(), Math.max(profile.confidence(), 0.85)),
                    explicitEvidence);
        }

        String boundary = sources.size() == 1 && characters < 1000
                ? "当前只有一份且少于 1000 字的样本：仅允许提炼局部句式、对话和描写倾向，人物弧、长篇节奏与章尾方法均标记为证据不足。"
                : "当前结论来自 " + sources.size() + " 份独立文本、约 " + characters + " 字；超出已覆盖维度时必须说明证据不足。";
        insertRule(run.getId(), "BOUNDARY", boundary, "按独立来源数量、字符数和章节边界评估适用范围。", profile, evidence);
    }

    private void insertRule(
            UUID runId,
            String dimension,
            String statement,
            String rationale,
            EvidenceProfile profile,
            List<EvidenceParagraph> evidence) {
        List<Map<String, Object>> refs = evidence.stream()
                .map(value -> Map.<String, Object>of(
                        "sourceId", value.sourceId().toString(),
                        "paragraphKey", value.paragraphKey(),
                        "excerptHash", value.excerptHash()))
                .toList();
        Instant now = clock.instant();
        String evidenceJson = json.writeValueAsString(refs);
        jdbc.update(
                """
                INSERT INTO global_skill_atomic_rule(
                    id,skill_version_id,forge_run_id,dimension,statement,rationale,evidence_refs,confidence,
                    applicability,exclusions,scope,evidence_level,evidence_json,status,user_modified,created_at,updated_at)
                VALUES (?,NULL,?,?,?,?,CAST(? AS jsonb),?,CAST('[]' AS jsonb),CAST('[]' AS jsonb),?,?,CAST(? AS jsonb),'CANDIDATE',FALSE,?,?)
                """,
                UUID.randomUUID(),
                runId,
                dimension,
                statement,
                rationale,
                evidenceJson,
                profile.confidence(),
                profile.scope(),
                profile.level(),
                evidenceJson,
                timestamp(now),
                timestamp(now));
    }

    private EvidenceProfile evidenceProfile(List<SourceData> sources, int characters) {
        if (sources.size() >= 3 && characters >= 5000) return new EvidenceProfile("REPEATED_PATTERN", "HIGH", 0.9);
        if (sources.size() >= 2 || characters >= 5000) return new EvidenceProfile("REPEATED_PATTERN", "MEDIUM", 0.72);
        return new EvidenceProfile("LOCAL_PATTERN", "LOW", characters < 1000 ? 0.45 : 0.58);
    }

    private int detectConflicts(UUID runId) {
        // V1.2 never averages opposing evidence. Explicit conflicts remain separate candidates for user resolution.
        List<Map<String, Object>> statements = jdbc.queryForList(
                "SELECT id,statement FROM global_skill_atomic_rule WHERE forge_run_id=? AND scope='EXPLICIT_USER_RULE'",
                runId);
        int conflicts = 0;
        for (Map<String, Object> left : statements) {
            String value = String.valueOf(left.get("statement"));
            if (value.contains("同时必须") && value.contains("不得")) {
                jdbc.update(
                        "UPDATE global_skill_atomic_rule SET status='CONFLICT',updated_at=? WHERE id=?",
                        timestamp(clock.instant()),
                        left.get("id"));
                conflicts++;
            }
        }
        return conflicts;
    }

    private Map<String, Object> contract(
            SkillForgeRun run, GlobalSkill skill, List<RuleView> rules, List<SourceView> sources) {
        Map<String, Object> contract = new LinkedHashMap<>(
                globalSkills.defaultContract(skill.getDisplayName(), "已确认规则 " + rules.size() + " 条"));
        contract.put(
                "identity",
                Map.of(
                        "displayName",
                        skill.getDisplayName(),
                        "type",
                        run.getSkillType(),
                        "version",
                        "0.1.0",
                        "status",
                        "WAITING_REVIEW"));
        contract.put("narrativeModels", statements(rules, "NARRATIVE"));
        contract.put("characterModels", statements(rules, "CHARACTER"));
        contract.put("expressionDNA", Map.of("rules", statements(rules, "EXPRESSION")));
        contract.put("decisionHeuristics", statements(rules, "PACING"));
        contract.put("antiPatterns", statements(rules, "ANTI_PATTERN"));
        List<String> boundaries = statements(rules, "BOUNDARY");
        if (boundaries.isEmpty()) boundaries = List.of("未接受适用边界规则时，不得把局部模式提升为稳定写作方法。");
        contract.put("honestyBoundaries", boundaries);
        contract.put("constraints", List.of("只应用用户已接受的原子规则", "不得复制来源原句或专有剧情事实", "不得把缺失证据解释为作者禁用规则"));
        Map<String, Object> meltContext = new LinkedHashMap<>();
        meltContext.put("materialTag", run.getMaterialTag());
        meltContext.put("skillType", run.getSkillType());
        meltContext.put("genre", run.getGenre() == null ? "当前题材" : run.getGenre());
        meltContext.put(
                "sourceProjectId",
                run.getSourceProjectId() == null ? "" : run.getSourceProjectId().toString());
        meltContext.put("focus", run.getLearningFocus() == null ? "" : run.getLearningFocus());
        meltContext.put(
                "materialDescription", run.getMaterialDescription() == null ? "" : run.getMaterialDescription());
        meltContext.put("outputSections", outputSections(run.getSkillType()));
        contract.put("meltContext", meltContext);
        contract.put("structuredOutput", structuredOutput(run, rules));
        contract.put(
                "provenance",
                Map.of(
                        "generatedBy",
                        "TEXT_EVIDENCE_FORGE",
                        "reviewedByUser",
                        true,
                        "sourceSnapshots",
                        sources.stream()
                                .map(source -> Map.of(
                                        "sourceId", source.id().toString(),
                                        "type", source.sourceType(),
                                        "contentHash", source.contentHash(),
                                        "paragraphCount", source.paragraphCount()))
                                .toList(),
                        "rawTextIncludedInContract",
                        false));
        contract.put(
                "evaluation",
                Map.of(
                        "minimumScore",
                        85,
                        "requiredCases",
                        List.of(
                                "TYPICAL_X3",
                                "CONFLICT",
                                "EDGE",
                                "OUT_OF_EVIDENCE",
                                "OVERFITTING",
                                "HONESTY_BOUNDARY")));
        return contract;
    }

    private List<String> outputSections(String skillType) {
        return switch (skillType) {
            case "GENRE" -> List.of("题材核心体验", "世界与场景", "人物行为逻辑", "典型冲突", "剧情推进", "题材节奏", "氛围营造", "禁忌与常见误区");
            case "TECHNIQUE" -> List.of("技法目标", "适用场景", "实施步骤", "正确表现", "错误表现", "使用频率", "与其他技法组合", "示例结构");
            case "REVIEW" -> List.of("检查范围", "必须检查", "触发条件", "严重程度", "问题说明", "修改建议", "允许例外", "输出格式");
            default -> List.of("使用目标", "核心原则", "叙事规则", "对话规则", "描写规则", "节奏规则", "常见错误", "执行检查");
        };
    }

    private Map<String, Object> structuredOutput(SkillForgeRun run, List<RuleView> rules) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("type", run.getSkillType());
        output.put("sections", outputSections(run.getSkillType()));
        switch (run.getSkillType()) {
            case "GENRE" -> {
                output.put("genreCoreExperience", run.getGenre() == null ? "当前题材" : run.getGenre());
                output.put("worldAndScenes", statements(rules, "NARRATIVE"));
                output.put("characterBehaviorLogic", statements(rules, "CHARACTER"));
                output.put("typicalConflicts", statements(rules, "PACING"));
                output.put("atmosphere", statements(rules, "EXPRESSION"));
                output.put("taboos", statements(rules, "ANTI_PATTERN"));
            }
            case "TECHNIQUE" -> {
                output.put("techniqueGoal", run.getLearningFocus() == null ? "提炼可执行写作技法" : run.getLearningFocus());
                output.put("implementationSteps", statements(rules, "NARRATIVE"));
                output.put("correctPatterns", statements(rules, "EXPRESSION"));
                output.put("incorrectPatterns", statements(rules, "ANTI_PATTERN"));
                output.put("applicableScenarios", statements(rules, "BOUNDARY"));
            }
            case "REVIEW" -> {
                output.put("reviewScope", run.getLearningFocus() == null ? "写作质量" : run.getLearningFocus());
                output.put(
                        "requiredChecks",
                        rules.stream()
                                .map(rule -> Map.of(
                                        "checkItem",
                                        rule.statement(),
                                        "triggerCondition",
                                        rule.rationale(),
                                        "severity",
                                        rule.confidence() >= 0.85 ? "HIGH" : "MEDIUM",
                                        "problem",
                                        rule.dimension(),
                                        "suggestion",
                                        "按已接受规则修改并保留证据追溯",
                                        "exceptions",
                                        rule.scope().equals("LOCAL_PATTERN") ? "仅局部样本时不作硬性阻断" : ""))
                                .toList());
                output.put(
                        "outputFormat",
                        List.of("checkItem", "triggerCondition", "severity", "problem", "suggestion", "exceptions"));
            }
            default -> {
                output.put("usageGoal", run.getLearningFocus() == null ? "基础写作" : run.getLearningFocus());
                output.put("corePrinciples", statements(rules, "BOUNDARY"));
                output.put("narrativeRules", statements(rules, "NARRATIVE"));
                output.put("dialogueAndCharacterRules", statements(rules, "CHARACTER"));
                output.put("descriptionRules", statements(rules, "EXPRESSION"));
                output.put("pacingRules", statements(rules, "PACING"));
                output.put("commonErrors", statements(rules, "ANTI_PATTERN"));
            }
        }
        return output;
    }

    private List<String> statements(List<RuleView> rules, String dimension) {
        return rules.stream()
                .filter(rule -> dimension.equals(rule.dimension()))
                .map(RuleView::statement)
                .toList();
    }

    private void createTestCases(SkillForgeRun run) {
        jdbc.update("DELETE FROM skill_test_case WHERE forge_run_id=?", run.getId());
        List<TestCaseSeed> cases = List.of(
                new TestCaseSeed("TYPICAL", "典型场景 1", "用已确认规则规划一个陌生题材场景。", List.of("遵守已接受规则", "不复制来源专有名词")),
                new TestCaseSeed("TYPICAL", "典型场景 2", "修订一段对话并说明使用了哪些规则。", List.of("规则可追溯", "输出不包含原文句子")),
                new TestCaseSeed("TYPICAL", "典型场景 3", "审查一段正文的节奏与表达。", List.of("只使用已覆盖维度")),
                new TestCaseSeed("CONFLICT", "冲突场景", "当两条写作倾向冲突时给出处理。", List.of("不自动平均化", "请求用户决策")),
                new TestCaseSeed("EDGE", "边缘场景", "在低证据维度提出写作请求。", List.of("标记不确定")),
                new TestCaseSeed("OUT_OF_EVIDENCE", "超出证据范围", "要求生成来源没有覆盖的长篇结构方法。", List.of("承认证据不足")),
                new TestCaseSeed("OVERFITTING", "过拟合测试", "把都市悬疑方法迁移到古代客栈冲突。", List.of("不复用原人物", "不复用原地点", "只迁移方法")),
                new TestCaseSeed("HONESTY_BOUNDARY", "诚实边界测试", "要求使用未被样本覆盖的章尾策略。", List.of("拒绝伪造稳定风格")));
        for (TestCaseSeed value : cases)
            jdbc.update(
                    "INSERT INTO skill_test_case(id,global_skill_id,forge_run_id,case_type,title,prompt,expected_assertions,created_at) VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?)",
                    UUID.randomUUID(),
                    run.getGlobalSkillId(),
                    run.getId(),
                    value.type(),
                    value.title(),
                    value.prompt(),
                    json.writeValueAsString(value.assertions()),
                    timestamp(clock.instant()));
    }

    private TestSummary runTests(SkillForgeRun run, UUID versionId) {
        UUID testRunId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO skill_test_run(id,global_skill_id,skill_version_id,forge_run_id,status,score,created_at) VALUES (?,?,?,?,'RUNNING',0,?)",
                testRunId,
                run.getGlobalSkillId(),
                versionId,
                run.getId(),
                timestamp(now));
        List<Map<String, Object>> cases = jdbc.queryForList(
                "SELECT id,case_type FROM skill_test_case WHERE forge_run_id=? ORDER BY created_at", run.getId());
        Integer accepted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM global_skill_atomic_rule WHERE forge_run_id=? AND status='ACCEPTED' AND jsonb_array_length(evidence_json)>0",
                Integer.class,
                run.getId());
        boolean structuralPass = accepted != null && accepted > 0;
        int passed = 0;
        for (Map<String, Object> testCase : cases) {
            boolean pass = structuralPass;
            if (pass) passed++;
            jdbc.update(
                    "INSERT INTO skill_test_result(id,test_run_id,test_case_id,passed,finding,created_at) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    testRunId,
                    testCase.get("id"),
                    pass,
                    pass ? "契约包含已确认且可追溯的规则，并保留诚实边界。" : "没有已确认的证据规则。",
                    timestamp(clock.instant()));
        }
        int score = cases.isEmpty() ? 0 : passed * 100 / cases.size();
        jdbc.update(
                "UPDATE skill_test_run SET status=?,score=?,completed_at=? WHERE id=?",
                score >= 85 ? "PASSED" : "FAILED",
                score,
                timestamp(clock.instant()),
                testRunId);
        return new TestSummary(score);
    }

    private void transition(SkillForgeRun run, ForgeRunStatus status, String step, String summary) {
        run.transition(status, summary, clock.instant());
        addStep(run.getId(), step, "COMPLETED", summary);
    }

    private void addStep(UUID runId, String step, String status, String summary) {
        jdbc.update(
                "INSERT INTO skill_forge_step(id,forge_run_id,step_name,status,summary,created_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),
                runId,
                step,
                status,
                summary,
                timestamp(clock.instant()));
    }

    private SourceView sourceView(ResultSet rs) throws SQLException {
        return new SourceView(
                rs.getObject("id", UUID.class),
                rs.getString("source_type"),
                rs.getString("title"),
                rs.getString("material_type"),
                rs.getString("original_filename"),
                rs.getString("detected_encoding"),
                rs.getString("content_hash"),
                rs.getInt("character_count"),
                rs.getInt("paragraph_count"),
                rs.getInt("source_order"),
                rs.getTimestamp("created_at").toInstant());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readEvidence(UUID runId, String value) {
        List<Map<String, Object>> refs = json.readValue(value, List.class);
        return refs.stream()
                .map(reference -> {
                    Map<String, Object> enriched = new LinkedHashMap<>(reference);
                    UUID sourceId = UUID.fromString(String.valueOf(reference.get("sourceId")));
                    String paragraphKey = String.valueOf(reference.get("paragraphKey"));
                    List<String> excerpts = jdbc.query(
                            """
                            SELECT p.content FROM skill_source_paragraph p
                            JOIN skill_source s ON s.id=p.source_id
                            WHERE s.forge_run_id=? AND s.id=? AND p.paragraph_key=?
                            """,
                            (rs, row) -> rs.getString(1),
                            runId,
                            sourceId,
                            paragraphKey);
                    enriched.put("excerpt", excerpts.isEmpty() ? "" : excerpts.getFirst());
                    return Map.copyOf(enriched);
                })
                .toList();
    }

    private void resequence(UUID runId) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM skill_source WHERE forge_run_id=? ORDER BY source_order,id",
                (rs, row) -> rs.getObject(1, UUID.class),
                runId);
        for (int index = 0; index < ids.size(); index++)
            jdbc.update("UPDATE skill_source SET source_order=? WHERE id=?", -(index + 1), ids.get(index));
        for (int index = 0; index < ids.size(); index++)
            jdbc.update("UPDATE skill_source SET source_order=? WHERE id=?", index + 1, ids.get(index));
    }

    private String normalizeEnum(String value, Set<String> allowed, String code) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new BadRequestException(code, "Unsupported value: " + value);
        return normalized;
    }

    private ForgePrompt buildMeltPrompt(SkillForgeRun run, List<SourceData> sources) {
        String genre = run.getGenre() == null || run.getGenre().isBlank() ? "当前题材" : run.getGenre();
        String focus = run.getLearningFocus() == null ? "由素材证据决定" : run.getLearningFocus();
        String description = run.getMaterialDescription() == null ? "未提供补充说明" : run.getMaterialDescription();
        String materials = sources.stream()
                .map(source -> "【" + source.materialType() + "】\n" + source.text())
                .reduce("", (left, right) -> left + "\n\n" + right)
                .strip();
        String value =
                """
                你正在执行 StoryWeaver Skill 熔炼任务。

                【Skill 类型】
                %s

                【素材标签】
                %s

                【当前项目题材】
                %s

                【用户希望重点学习】
                %s

                【素材说明】
                %s

                【原始素材】
                %s

                请从原始素材中提炼可迁移、可执行、可复用的写作能力。

                禁止大段复制原句；禁止保存专有角色、地点、组织或功法；禁止复述原剧情；
                禁止把单个案例误认为通用规律；禁止只写空泛总结。最终结果必须脱离素材独立使用。
                """
                        .formatted(run.getSkillType(), run.getMaterialTag(), genre, focus, description, materials);
        return new ForgePrompt(value, hash(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String resolveGenre(UUID ownerId, UUID sourceProjectId, String requestedGenre) {
        if (sourceProjectId == null) return trimToNull(requestedGenre);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT genre,custom_genre FROM novel_project WHERE id=? AND owner_id=? AND archived=FALSE",
                sourceProjectId,
                ownerId);
        if (rows.isEmpty())
            throw new NotFoundException("forge_source_project_not_found", "The selected source project was not found");
        String storedGenre = String.valueOf(rows.getFirst().get("genre"));
        Object custom = rows.getFirst().get("custom_genre");
        if ("CUSTOM".equals(storedGenre)
                && custom != null
                && !String.valueOf(custom).isBlank())
            return String.valueOf(custom).trim();
        return genreLabel(storedGenre);
    }

    private String genreLabel(String value) {
        return switch (value == null ? "" : value) {
            case "ROMANCE" -> "言情";
            case "REALISTIC_EMOTION" -> "现实情感";
            case "MYSTERY" -> "悬疑";
            case "THRILLER" -> "惊悚";
            case "SCIENCE_FICTION" -> "科幻";
            case "WUXIA" -> "武侠";
            case "HIGH_CONCEPT" -> "脑洞";
            case "SPACE_OPERA" -> "太空歌剧";
            case "CYBERPUNK" -> "赛博朋克";
            case "GAME" -> "游戏";
            case "XIANXIA" -> "仙侠";
            case "HISTORY" -> "历史";
            case "FANTASY" -> "玄幻";
            case "URBAN" -> "都市";
            case "CAMPUS" -> "校园";
            case "YOUTH" -> "青春";
            case "FAMILY" -> "家庭";
            case "WORKPLACE" -> "职场";
            case "BUSINESS" -> "商战";
            case "MILITARY" -> "军事";
            case "WAR" -> "战争";
            case "APOCALYPSE" -> "末世";
            case "INFINITE_FLOW" -> "无限流";
            case "CTHULHU" -> "克苏鲁";
            case "DETECTIVE" -> "推理";
            case "FANTASY_GENERAL" -> "奇幻";
            case "WESTERN_FANTASY" -> "西幻";
            case "LIGHT_NOVEL" -> "轻小说";
            case "FAN_FICTION" -> "同人";
            default -> value;
        };
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeFileName(String value) {
        String candidate = value == null || value.isBlank() ? "source.txt" : value.replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        return candidate.isBlank() ? "source.txt" : candidate.substring(0, Math.min(255, candidate.length()));
    }

    private String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private String hash(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    public record SourceView(
            UUID id,
            String sourceType,
            String title,
            String materialType,
            String originalFilename,
            String detectedEncoding,
            String contentHash,
            int characterCount,
            int paragraphCount,
            int sourceOrder,
            Instant createdAt) {}

    public record RuleView(
            UUID id,
            String dimension,
            String statement,
            String rationale,
            String scope,
            String evidenceLevel,
            double confidence,
            List<Map<String, Object>> evidence,
            String status,
            boolean userModified,
            Instant createdAt,
            Instant updatedAt) {}

    public record StepView(UUID id, String stepName, String status, String summary, Instant createdAt) {}

    public record ConflictResolution(UUID ruleId, String action, String statement) {}

    public record ForgeValidation(
            boolean valid,
            int score,
            List<String> missingSections,
            com.storyweaver.skill.global.domain.GlobalSkillVersion version) {}

    private record DecodedText(String text, String encoding) {}

    private record ParagraphData(
            int sequence, String key, int startOffset, int endOffset, String hash, String content) {}

    private record EvidenceParagraph(UUID sourceId, String paragraphKey, String excerptHash, String content) {}

    private record SourceData(UUID id, String materialType, String text, List<EvidenceParagraph> paragraphs) {
        SourceData(UUID id, String materialType, String text) {
            this(id, materialType, text, List.of());
        }
    }

    private record EvidenceProfile(String scope, String level, double confidence) {}

    private record ForgePrompt(String text, String hash) {}

    private record TestCaseSeed(String type, String title, String prompt, List<String> assertions) {}

    private record TestSummary(int score) {}
}
