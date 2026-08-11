package com.storyweaver.importing.book.application;

import com.storyweaver.chapter.application.ChapterService;
import com.storyweaver.importing.book.config.TxtImportProperties;
import com.storyweaver.importing.book.domain.TxtImportModels.ChapterCandidate;
import com.storyweaver.importing.book.domain.TxtImportModels.ImportView;
import com.storyweaver.importing.book.domain.TxtImportModels.ParseResult;
import com.storyweaver.importing.book.domain.TxtImportModels.ParsedChapter;
import com.storyweaver.importing.book.parser.TxtChapterParser;
import com.storyweaver.importing.book.parser.TxtEncodingDetector;
import com.storyweaver.importing.book.parser.TxtTextReader;
import com.storyweaver.importing.book.parser.TxtTextReader.TextRange;
import com.storyweaver.importing.book.storage.ImportSourceStorage;
import com.storyweaver.project.application.ProjectService;
import com.storyweaver.project.domain.LengthType;
import com.storyweaver.project.domain.NarrativePerspective;
import com.storyweaver.project.domain.TargetAudience;
import com.storyweaver.shared.error.ApiException;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TxtBookImportService {
    private static final Set<String> MUTABLE = Set.of("UPLOADED", "PARSED", "WAITING_CONFIRMATION", "FAILED");

    private final JdbcTemplate jdbc;
    private final ImportSourceStorage storage;
    private final TxtEncodingDetector encodingDetector;
    private final TxtChapterParser parser;
    private final TxtTextReader textReader;
    private final ProjectService projectService;
    private final ChapterService chapterService;
    private final TxtImportProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final EntityManager entityManager;

    public TxtBookImportService(
            JdbcTemplate jdbc,
            ImportSourceStorage storage,
            TxtEncodingDetector encodingDetector,
            TxtChapterParser parser,
            TxtTextReader textReader,
            ProjectService projectService,
            ChapterService chapterService,
            TxtImportProperties properties,
            PlatformTransactionManager transactionManager,
            Clock clock,
            EntityManager entityManager) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.encodingDetector = encodingDetector;
        this.parser = parser;
        this.textReader = textReader;
        this.projectService = projectService;
        this.chapterService = chapterService;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.entityManager = entityManager;
    }

    public ImportView upload(UUID ownerId, MultipartFile file) {
        validateUpload(file);
        ImportSourceStorage.StoredFile stored;
        try {
            stored = storage.store(file.getInputStream());
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILURE", "TXT source could not be read");
        }
        try {
            Path path = storage.path(stored.storageKey());
            TxtEncodingDetector.Detection detection = encodingDetector.detect(path);
            Instant now = clock.instant();
            UUID sourceId = UUID.randomUUID();
            UUID importId = UUID.randomUUID();
            transactions.executeWithoutResult(status -> {
                jdbc.update(
                        "INSERT INTO book_import_source(id,owner_id,original_filename,storage_key,size_bytes,sha256,raw_content_hash,detected_encoding,selected_encoding,encoding_confident,expires_at,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                        sourceId,
                        ownerId,
                        safeFilename(file.getOriginalFilename()),
                        stored.storageKey(),
                        stored.sizeBytes(),
                        stored.sha256(),
                        stored.sha256(),
                        detection.detectedEncoding(),
                        detection.selectedEncoding(),
                        detection.confident(),
                        timestamp(now.plus(properties.sourceRetention())),
                        timestamp(now));
                jdbc.update(
                        "INSERT INTO book_import_job(id,owner_id,source_id,status,parser_version,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                        importId,
                        ownerId,
                        sourceId,
                        "UPLOADED",
                        TxtChapterParser.PARSER_VERSION,
                        timestamp(now),
                        timestamp(now));
            });
            return get(importId, ownerId);
        } catch (RuntimeException exception) {
            storage.delete(stored.storageKey());
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public ImportView get(UUID importId, UUID ownerId) {
        ImportRow row = requireRow(importId, ownerId);
        Duplicate duplicate = duplicate(row.sourceId(), ownerId, row.sha256());
        return toView(row, duplicate, chapters(importId));
    }

    public ImportView parse(UUID importId, UUID ownerId, String requestedEncoding) {
        ImportRow row = requireRow(importId, ownerId);
        requireMutable(row.status());
        Path path = storage.path(row.storageKey());
        String selected = selectEncoding(requestedEncoding, row.selectedEncoding());
        Charset charset = encodingDetector.charset(selected);
        encodingDetector.validate(path, charset);
        updateState(importId, "DECODING", null, null);
        ParseResult result;
        try {
            result = parser.parse(path, charset, stripTxt(row.filename()));
        } catch (RuntimeException exception) {
            updateState(importId, "FAILED", code(exception, "CHAPTER_PARSE_FAILED"), safeMessage(exception));
            throw exception;
        }
        jdbc.update("DELETE FROM book_import_chapter WHERE import_id=?", importId);
        int sequence = 1;
        Instant now = clock.instant();
        for (ParsedChapter chapter : result.chapters()) {
            jdbc.update(
                    "INSERT INTO book_import_chapter(id,import_id,sequence_no,title,start_offset,end_offset,character_count,paragraph_count,included,created_at) VALUES (?,?,?,?,?,?,?,?,TRUE,?)",
                    UUID.randomUUID(),
                    importId,
                    sequence++,
                    chapter.title(),
                    chapter.startOffset(),
                    chapter.endOffset(),
                    chapter.characterCount(),
                    chapter.paragraphCount(),
                    timestamp(now));
        }
        jdbc.update(
                "UPDATE book_import_source SET selected_encoding=?, normalized_content_hash=?, character_count=? WHERE id=?",
                selected,
                result.normalizedHash(),
                result.characterCount(),
                row.sourceId());
        jdbc.update(
                "UPDATE book_import_job SET status='PARSED',total_characters=?,total_chapters=?,processed_chapters=0,heading_count=?,error_code=NULL,error_message=NULL,version=version+1,updated_at=? WHERE id=?",
                result.characterCount(),
                result.chapters().size(),
                result.headingCount(),
                timestamp(now),
                importId);
        updateState(importId, "WAITING_CONFIRMATION", null, null);
        return get(importId, ownerId);
    }

    @Transactional(readOnly = true)
    public String previewContent(UUID importId, UUID chapterId, UUID ownerId, Integer requestedLimit) {
        ImportRow row = requireRow(importId, ownerId);
        ChapterCandidate chapter = chapter(importId, chapterId);
        int limit = requestedLimit == null
                ? properties.previewMaxCharacters()
                : Math.max(1, Math.min(properties.previewMaxCharacters(), requestedLimit));
        return textReader.readRange(
                storage.path(row.storageKey()),
                encodingDetector.charset(row.selectedEncoding()),
                chapter.startOffset(),
                chapter.endOffset(),
                limit);
    }

    @Transactional
    public ImportView updateChapter(
            UUID importId, UUID chapterId, UUID ownerId, long expectedVersion, String title, boolean included) {
        ImportRow row = requireEditable(importId, ownerId, expectedVersion);
        if (title == null || title.isBlank() || title.strip().length() > 160) {
            throw new BadRequestException("CHAPTER_PARSE_FAILED", "Chapter title must contain 1 to 160 characters");
        }
        int updated = jdbc.update(
                "UPDATE book_import_chapter SET title=?,included=? WHERE id=? AND import_id=?",
                title.strip(),
                included,
                chapterId,
                importId);
        if (updated == 0) throw new NotFoundException("IMPORT_NOT_FOUND", "Import chapter was not found");
        touch(row.id());
        return get(importId, ownerId);
    }

    @Transactional
    public ImportView reorder(UUID importId, UUID ownerId, long expectedVersion, List<UUID> chapterIds) {
        ImportRow row = requireEditable(importId, ownerId, expectedVersion);
        List<ChapterCandidate> current = chapters(importId);
        if (chapterIds == null
                || chapterIds.size() != current.size()
                || new HashSet<>(chapterIds).size() != current.size()
                || !new HashSet<>(chapterIds)
                        .equals(current.stream()
                                .map(ChapterCandidate::id)
                                .collect(java.util.stream.Collectors.toSet()))) {
            throw new BadRequestException(
                    "CHAPTER_PARSE_FAILED", "Reorder must contain every import chapter exactly once");
        }
        jdbc.update("UPDATE book_import_chapter SET sequence_no=sequence_no+10000 WHERE import_id=?", importId);
        for (int index = 0; index < chapterIds.size(); index++) {
            jdbc.update(
                    "UPDATE book_import_chapter SET sequence_no=? WHERE id=? AND import_id=?",
                    index + 1,
                    chapterIds.get(index),
                    importId);
        }
        touch(row.id());
        return get(importId, ownerId);
    }

    @Transactional
    public ImportView merge(
            UUID importId, UUID ownerId, long expectedVersion, UUID firstId, UUID secondId, String title) {
        ImportRow row = requireEditable(importId, ownerId, expectedVersion);
        ChapterCandidate first = chapter(importId, firstId);
        ChapterCandidate second = chapter(importId, secondId);
        if (second.sequenceNo() != first.sequenceNo() + 1) {
            throw new ConflictException("CHAPTER_PARSE_FAILED", "Only adjacent chapters can be merged");
        }
        String mergedTitle = title == null || title.isBlank() ? first.title() : title.strip();
        if (mergedTitle.length() > 160) {
            throw new BadRequestException("CHAPTER_PARSE_FAILED", "Merged chapter title exceeds 160 characters");
        }
        long start = Math.min(first.startOffset(), second.startOffset());
        long end = Math.max(first.endOffset(), second.endOffset());
        jdbc.update(
                "UPDATE book_import_chapter SET title=?,start_offset=?,end_offset=?,character_count=?,paragraph_count=?,included=? WHERE id=?",
                mergedTitle,
                start,
                end,
                end - start,
                first.paragraphCount() + second.paragraphCount(),
                first.included() || second.included(),
                first.id());
        jdbc.update("DELETE FROM book_import_chapter WHERE id=?", second.id());
        resequence(importId);
        touch(row.id());
        return get(importId, ownerId);
    }

    @Transactional
    public ImportView split(
            UUID importId, UUID ownerId, long expectedVersion, UUID chapterId, long splitOffset, String secondTitle) {
        ImportRow row = requireEditable(importId, ownerId, expectedVersion);
        ChapterCandidate current = chapter(importId, chapterId);
        if (splitOffset <= 0 || splitOffset >= current.characterCount()) {
            throw new BadRequestException("CHAPTER_PARSE_FAILED", "Split offset must be inside the chapter");
        }
        String title = secondTitle == null || secondTitle.isBlank() ? current.title() + "（下）" : secondTitle.strip();
        if (title.length() > 160)
            throw new BadRequestException("CHAPTER_PARSE_FAILED", "Chapter title exceeds 160 characters");
        long boundary = current.startOffset() + splitOffset;
        List<ChapterCandidate> later = chapters(importId).stream()
                .filter(value -> value.sequenceNo() > current.sequenceNo())
                .toList();
        jdbc.update(
                "UPDATE book_import_chapter SET sequence_no=sequence_no+10000 WHERE import_id=? AND sequence_no>?",
                importId,
                current.sequenceNo());
        for (ChapterCandidate value : later) {
            jdbc.update("UPDATE book_import_chapter SET sequence_no=? WHERE id=?", value.sequenceNo() + 1, value.id());
        }
        jdbc.update(
                "UPDATE book_import_chapter SET end_offset=?,character_count=? WHERE id=?",
                boundary,
                boundary - current.startOffset(),
                current.id());
        jdbc.update(
                "INSERT INTO book_import_chapter(id,import_id,sequence_no,title,start_offset,end_offset,character_count,paragraph_count,included,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                importId,
                current.sequenceNo() + 1,
                title,
                boundary,
                current.endOffset(),
                current.endOffset() - boundary,
                0,
                current.included(),
                timestamp(clock.instant()));
        updateTotalChapters(importId);
        touch(row.id());
        return get(importId, ownerId);
    }

    @Transactional
    public ImportView wholeBook(UUID importId, UUID ownerId, long expectedVersion, String title) {
        ImportRow row = requireEditable(importId, ownerId, expectedVersion);
        if (row.totalCharacters() <= 0) throw new ConflictException("CHAPTER_PARSE_FAILED", "TXT must be parsed first");
        replaceCandidates(
                importId,
                List.of(new ParsedChapter(
                        normalizeTitle(title, stripTxt(row.filename())),
                        0,
                        row.totalCharacters(),
                        row.totalCharacters(),
                        0)));
        touch(row.id());
        return get(importId, ownerId);
    }

    @Transactional
    public ImportView fixedSplit(UUID importId, UUID ownerId, long expectedVersion, int targetCharacters) {
        ImportRow row = requireEditable(importId, ownerId, expectedVersion);
        if (targetCharacters < 1_000 || targetCharacters > 100_000) {
            throw new BadRequestException(
                    "CHAPTER_PARSE_FAILED", "Fixed split target must be between 1,000 and 100,000 characters");
        }
        Path path = storage.path(row.storageKey());
        Charset charset = encodingDetector.charset(row.selectedEncoding());
        List<ParsedChapter> values = new ArrayList<>();
        long[] start = {0};
        long[] lastParagraphBoundary = {0};
        int[] paragraphs = {0};
        boolean[] inParagraph = {false};
        textReader.forEachNormalizedLine(path, charset, (line, segment, offset) -> {
            long end = offset + segment.length();
            if (line.isBlank()) {
                inParagraph[0] = false;
                lastParagraphBoundary[0] = end;
            } else if (!inParagraph[0]) {
                paragraphs[0]++;
                inParagraph[0] = true;
            }
            if (end - start[0] >= targetCharacters) {
                long boundary = lastParagraphBoundary[0] > start[0] ? lastParagraphBoundary[0] : end;
                if (boundary > start[0]) {
                    values.add(new ParsedChapter(
                            "片段 " + String.format(Locale.ROOT, "%03d", values.size() + 1),
                            start[0],
                            boundary,
                            boundary - start[0],
                            paragraphs[0]));
                    start[0] = boundary;
                    paragraphs[0] = 0;
                    inParagraph[0] = false;
                }
            }
        });
        if (row.totalCharacters() > start[0]) {
            values.add(new ParsedChapter(
                    "片段 " + String.format(Locale.ROOT, "%03d", values.size() + 1),
                    start[0],
                    row.totalCharacters(),
                    row.totalCharacters() - start[0],
                    paragraphs[0]));
        }
        replaceCandidates(importId, values);
        touch(row.id());
        return get(importId, ownerId);
    }

    public ImportView commit(UUID importId, UUID ownerId, long expectedVersion, ProjectInput input) {
        ImportRow row = requireRow(importId, ownerId);
        requireExpected(row, expectedVersion);
        if (!"WAITING_CONFIRMATION".equals(row.status())) {
            throw new ConflictException("IMPORT_ALREADY_COMMITTED", "TXT import is not waiting for confirmation");
        }
        List<ChapterCandidate> selected =
                chapters(importId).stream().filter(ChapterCandidate::included).toList();
        if (selected.isEmpty())
            throw new BadRequestException("NO_TEXT_CONTENT", "At least one chapter must be included");
        validateProject(input);
        updateState(importId, "IMPORTING", null, null);
        try {
            UUID projectId = transactions.execute(status -> commitProject(row, ownerId, input, selected));
            if (projectId == null) throw new IllegalStateException("Project transaction returned no project");
            return get(importId, ownerId);
        } catch (RuntimeException exception) {
            updateState(importId, "FAILED", code(exception, "PROJECT_CREATE_FAILED"), safeMessage(exception));
            throw exception;
        }
    }

    @Transactional
    public ImportView cancel(UUID importId, UUID ownerId) {
        ImportRow row = requireRow(importId, ownerId);
        if ("COMPLETED".equals(row.status()) || "IMPORTING".equals(row.status())) {
            throw new ConflictException("IMPORT_ALREADY_COMMITTED", "Committed TXT import cannot be cancelled");
        }
        updateState(importId, "CANCELLED", null, null);
        return get(importId, ownerId);
    }

    @Scheduled(fixedDelayString = "${storyweaver.import.txt.cleanup-interval:1h}")
    public void cleanupExpiredSources() {
        Instant now = clock.instant();
        List<ExpiredSource> expired = jdbc.query(
                "SELECT id,storage_key FROM book_import_source WHERE storage_key IS NOT NULL AND expires_at<?",
                (rs, index) -> new ExpiredSource(rs.getObject("id", UUID.class), rs.getString("storage_key")),
                timestamp(now));
        for (ExpiredSource source : expired) {
            try {
                storage.delete(source.storageKey());
                jdbc.update(
                        "UPDATE book_import_source SET storage_key=NULL,deleted_at=? WHERE id=? AND storage_key=?",
                        timestamp(now),
                        source.id(),
                        source.storageKey());
                jdbc.update(
                        "UPDATE book_import_job SET status='CANCELLED',error_code='IMPORT_EXPIRED',error_message='Temporary TXT source expired',version=version+1,updated_at=? WHERE source_id=? AND status NOT IN ('COMPLETED','CANCELLED')",
                        timestamp(now),
                        source.id());
            } catch (RuntimeException ignored) {
                // A later idempotent cleanup pass retries failed filesystem deletes.
            }
        }
    }

    private UUID commitProject(ImportRow row, UUID ownerId, ProjectInput input, List<ChapterCandidate> selected) {
        var project = projectService.create(
                ownerId,
                input.name(),
                input.genre(),
                input.customGenre(),
                input.targetAudience(),
                input.narrativePerspective(),
                input.lengthType(),
                input.premise(),
                input.description(),
                input.authorIntent(),
                input.currentFocus(),
                input.worldRules(),
                input.targetWordCount(),
                input.chapterWordTarget(),
                input.baseSkillVersionId());
        entityManager.flush();
        jdbc.update(
                "UPDATE novel_project SET creation_source='TXT_IMPORT',source_hash=?,source_encoding=?,parser_version=? WHERE id=?",
                row.sha256(),
                row.selectedEncoding(),
                TxtChapterParser.PARSER_VERSION,
                project.getId());
        LinkedHashMap<UUID, Integer> chapterNumbers = new LinkedHashMap<>();
        LinkedHashMap<UUID, ChapterCandidate> byId = new LinkedHashMap<>();
        int chapterNo = 1;
        for (ChapterCandidate candidate : selected) {
            chapterNumbers.put(candidate.id(), chapterNo++);
            byId.put(candidate.id(), candidate);
        }
        int[] processed = {0};
        Path path = storage.path(row.storageKey());
        Charset charset = encodingDetector.charset(row.selectedEncoding());
        List<TextRange> ranges = selected.stream()
                .map(candidate -> new TextRange(candidate.id(), candidate.startOffset(), candidate.endOffset()))
                .toList();
        textReader.forEachRange(path, charset, ranges, (candidateId, content) -> {
            ChapterCandidate candidate = byId.get(candidateId);
            var created = chapterService.create(
                    project.getId(), ownerId, chapterNumbers.get(candidateId), candidate.title(), null, null);
            var versioned = chapterService.addVersion(
                    created.chapter().getId(),
                    ownerId,
                    created.chapter().getVersion(),
                    candidate.title(),
                    content,
                    null,
                    "Imported from TXT source " + row.sha256());
            jdbc.update(
                    "UPDATE chapter SET import_source_id=?,source_start_offset=?,source_end_offset=?,source_hash=? WHERE id=?",
                    row.sourceId(),
                    candidate.startOffset(),
                    candidate.endOffset(),
                    row.sha256(),
                    created.chapter().getId());
            jdbc.update(
                    "UPDATE chapter_version SET creation_source='TXT_IMPORT',import_source_id=?,source_start_offset=?,source_end_offset=?,source_hash=?,source_encoding=?,parser_version=? WHERE id=?",
                    row.sourceId(),
                    candidate.startOffset(),
                    candidate.endOffset(),
                    row.sha256(),
                    row.selectedEncoding(),
                    TxtChapterParser.PARSER_VERSION,
                    versioned.currentVersion().getId());
            jdbc.update(
                    "UPDATE book_import_chapter SET created_chapter_id=? WHERE id=?",
                    created.chapter().getId(),
                    candidate.id());
            processed[0]++;
            jdbc.update("UPDATE book_import_job SET processed_chapters=? WHERE id=?", processed[0], row.id());
        });
        jdbc.update(
                "UPDATE book_import_job SET project_id=?,status='COMPLETED',total_chapters=?,processed_chapters=?,error_code=NULL,error_message=NULL,version=version+1,updated_at=? WHERE id=?",
                project.getId(),
                selected.size(),
                selected.size(),
                timestamp(clock.instant()),
                row.id());
        return project.getId();
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("EMPTY_FILE", "TXT file is empty");
        String filename = safeFilename(file.getOriginalFilename());
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new BadRequestException("UNSUPPORTED_FILE_TYPE", "Only .txt files are supported");
        }
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new BadRequestException("FILE_TOO_LARGE", "TXT file exceeds 20 MB");
        }
    }

    private void validateProject(ProjectInput input) {
        if (input == null
                || input.name() == null
                || input.name().isBlank()
                || input.name().strip().length() > 80
                || input.genre() == null
                || input.genre().isBlank()
                || input.premise() == null
                || input.premise().strip().length() < 10
                || input.targetAudience() == null
                || input.narrativePerspective() == null
                || input.lengthType() == null
                || input.worldRules() == null) {
            throw new BadRequestException("PROJECT_CREATE_FAILED", "Project settings are incomplete");
        }
    }

    private ImportRow requireEditable(UUID importId, UUID ownerId, long expectedVersion) {
        ImportRow row = requireRow(importId, ownerId);
        requireExpected(row, expectedVersion);
        if (!"WAITING_CONFIRMATION".equals(row.status())) {
            throw new ConflictException("IMPORT_ALREADY_COMMITTED", "TXT import preview is not editable");
        }
        return row;
    }

    private void requireExpected(ImportRow row, long expectedVersion) {
        if (row.version() != expectedVersion) {
            throw new ConflictException("optimistic_lock_conflict", "TXT import changed; reload before retrying");
        }
    }

    private void requireMutable(String status) {
        if (!MUTABLE.contains(status)) {
            throw new ConflictException("IMPORT_ALREADY_COMMITTED", "TXT import cannot be parsed in its current state");
        }
    }

    private ImportRow requireRow(UUID importId, UUID ownerId) {
        ImportRow row = jdbc.query(
                "SELECT j.*,s.original_filename,s.storage_key,s.size_bytes,s.sha256,s.detected_encoding,s.selected_encoding,s.encoding_confident,s.expires_at FROM book_import_job j JOIN book_import_source s ON s.id=j.source_id WHERE j.id=? AND j.owner_id=?",
                rs -> rs.next()
                        ? new ImportRow(
                                rs.getObject("id", UUID.class),
                                rs.getObject("source_id", UUID.class),
                                rs.getObject("project_id", UUID.class),
                                rs.getString("status"),
                                rs.getString("analysis_status"),
                                rs.getString("original_filename"),
                                rs.getString("storage_key"),
                                rs.getLong("size_bytes"),
                                rs.getString("sha256"),
                                rs.getString("detected_encoding"),
                                rs.getString("selected_encoding"),
                                rs.getBoolean("encoding_confident"),
                                rs.getLong("total_characters"),
                                rs.getInt("total_chapters"),
                                rs.getInt("processed_chapters"),
                                rs.getInt("heading_count"),
                                rs.getInt("analysis_processed_chunks"),
                                rs.getString("parser_version"),
                                rs.getString("error_code"),
                                rs.getString("error_message"),
                                rs.getLong("version"),
                                rs.getTimestamp("expires_at").toInstant(),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant())
                        : null,
                importId,
                ownerId);
        if (row == null) throw new NotFoundException("IMPORT_NOT_FOUND", "TXT import was not found");
        return row;
    }

    private List<ChapterCandidate> chapters(UUID importId) {
        return jdbc.query(
                "SELECT * FROM book_import_chapter WHERE import_id=? ORDER BY sequence_no",
                (rs, index) -> new ChapterCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getInt("sequence_no"),
                        rs.getString("title"),
                        rs.getLong("start_offset"),
                        rs.getLong("end_offset"),
                        rs.getLong("character_count"),
                        rs.getInt("paragraph_count"),
                        rs.getBoolean("included")),
                importId);
    }

    private ChapterCandidate chapter(UUID importId, UUID chapterId) {
        return chapters(importId).stream()
                .filter(value -> value.id().equals(chapterId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "Import chapter was not found"));
    }

    private Duplicate duplicate(UUID sourceId, UUID ownerId, String sha256) {
        Duplicate value = jdbc.query(
                "SELECT j.id,j.project_id FROM book_import_job j JOIN book_import_source s ON s.id=j.source_id WHERE j.owner_id=? AND s.sha256=? AND s.id<>? ORDER BY (j.project_id IS NOT NULL) DESC,j.created_at DESC LIMIT 1",
                rs -> rs.next()
                        ? new Duplicate(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class))
                        : null,
                ownerId,
                sha256,
                sourceId);
        return value == null ? new Duplicate(null, null) : value;
    }

    private ImportView toView(ImportRow row, Duplicate duplicate, List<ChapterCandidate> values) {
        return new ImportView(
                row.id(),
                row.sourceId(),
                row.projectId(),
                row.status(),
                row.analysisStatus(),
                row.filename(),
                row.sizeBytes(),
                row.sha256(),
                row.detectedEncoding(),
                row.selectedEncoding(),
                row.encodingConfident(),
                row.totalCharacters(),
                row.totalChapters(),
                row.processedChapters(),
                row.headingCount(),
                row.analysisProcessedChunks(),
                row.parserVersion(),
                row.errorCode(),
                row.errorMessage(),
                duplicate.importId(),
                duplicate.projectId(),
                row.version(),
                row.expiresAt(),
                row.createdAt(),
                row.updatedAt(),
                values);
    }

    private void replaceCandidates(UUID importId, List<ParsedChapter> values) {
        if (values.isEmpty()) throw new BadRequestException("NO_TEXT_CONTENT", "No chapter split was produced");
        jdbc.update("DELETE FROM book_import_chapter WHERE import_id=?", importId);
        int sequence = 1;
        for (ParsedChapter value : values) {
            jdbc.update(
                    "INSERT INTO book_import_chapter(id,import_id,sequence_no,title,start_offset,end_offset,character_count,paragraph_count,included,created_at) VALUES (?,?,?,?,?,?,?,?,TRUE,?)",
                    UUID.randomUUID(),
                    importId,
                    sequence++,
                    value.title(),
                    value.startOffset(),
                    value.endOffset(),
                    value.characterCount(),
                    value.paragraphCount(),
                    timestamp(clock.instant()));
        }
        updateTotalChapters(importId);
    }

    private void resequence(UUID importId) {
        List<UUID> ids = chapters(importId).stream().map(ChapterCandidate::id).toList();
        jdbc.update("UPDATE book_import_chapter SET sequence_no=sequence_no+10000 WHERE import_id=?", importId);
        for (int index = 0; index < ids.size(); index++) {
            jdbc.update("UPDATE book_import_chapter SET sequence_no=? WHERE id=?", index + 1, ids.get(index));
        }
        updateTotalChapters(importId);
    }

    private void updateTotalChapters(UUID importId) {
        jdbc.update(
                "UPDATE book_import_job SET total_chapters=(SELECT COUNT(*) FROM book_import_chapter WHERE import_id=?) WHERE id=?",
                importId,
                importId);
    }

    private void touch(UUID importId) {
        jdbc.update(
                "UPDATE book_import_job SET version=version+1,updated_at=? WHERE id=?",
                timestamp(clock.instant()),
                importId);
    }

    private void updateState(UUID importId, String status, String errorCode, String errorMessage) {
        jdbc.update(
                "UPDATE book_import_job SET status=?,error_code=?,error_message=?,version=version+1,updated_at=? WHERE id=?",
                status,
                errorCode,
                errorMessage,
                timestamp(clock.instant()),
                importId);
    }

    private String selectEncoding(String requested, String detectedSelection) {
        String value = requested == null || requested.isBlank()
                ? "AUTO"
                : requested.strip().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "AUTO" -> detectedSelection;
            case "UTF-8", "UTF_8" -> "UTF-8";
            case "GB18030" -> "GB18030";
            case "GBK" -> "GBK";
            default -> throw new BadRequestException("INVALID_TEXT_ENCODING", "Unsupported TXT encoding");
        };
    }

    private String safeFilename(String value) {
        String safe = value == null ? "book.txt" : value.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).strip();
        if (safe.isBlank()) safe = "book.txt";
        if (safe.length() <= 255) return safe;
        return safe.toLowerCase(Locale.ROOT).endsWith(".txt")
                ? safe.substring(0, 251) + ".txt"
                : safe.substring(0, 255);
    }

    private String stripTxt(String value) {
        return value.toLowerCase(Locale.ROOT).endsWith(".txt") ? value.substring(0, value.length() - 4) : value;
    }

    private String normalizeTitle(String value, String fallback) {
        String title = value == null || value.isBlank() ? fallback : value.strip();
        return title.substring(0, Math.min(160, title.length()));
    }

    private String safeMessage(RuntimeException exception) {
        String value = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return value.substring(0, Math.min(500, value.length()));
    }

    private String code(RuntimeException exception, String fallback) {
        return exception instanceof ApiException api ? api.getCode() : fallback;
    }

    private Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    public record ProjectInput(
            String name,
            String genre,
            String customGenre,
            TargetAudience targetAudience,
            NarrativePerspective narrativePerspective,
            LengthType lengthType,
            String premise,
            String description,
            String authorIntent,
            String currentFocus,
            List<String> worldRules,
            Integer targetWordCount,
            Integer chapterWordTarget,
            UUID baseSkillVersionId) {}

    private record ImportRow(
            UUID id,
            UUID sourceId,
            UUID projectId,
            String status,
            String analysisStatus,
            String filename,
            String storageKey,
            long sizeBytes,
            String sha256,
            String detectedEncoding,
            String selectedEncoding,
            boolean encodingConfident,
            long totalCharacters,
            int totalChapters,
            int processedChapters,
            int headingCount,
            int analysisProcessedChunks,
            String parserVersion,
            String errorCode,
            String errorMessage,
            long version,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt) {}

    private record Duplicate(UUID importId, UUID projectId) {}

    private record ExpiredSource(UUID id, String storageKey) {}
}
