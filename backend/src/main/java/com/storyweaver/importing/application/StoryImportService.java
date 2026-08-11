package com.storyweaver.importing.application;

import com.storyweaver.chapter.application.ChapterService;
import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.application.AgentContracts.ExtractionResult;
import com.storyweaver.llm.application.ExtractorGateway;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StoryImportService {
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "(?m)^(第[0-9一二三四五六七八九十百千万零〇两]+章[^\\r\\n]*|Chapter\\s+\\d+[^\\r\\n]*)$", Pattern.CASE_INSENSITIVE);
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final int MAX_CHAPTERS = 500;
    private static final int MAX_CHAPTER_CHARACTERS = 500_000;

    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccess;
    private final ChapterService chapters;
    private final ExtractorGateway extractor;
    private final Clock clock;

    public StoryImportService(
            JdbcTemplate jdbc,
            ProjectAccessService projectAccess,
            ChapterService chapters,
            ExtractorGateway extractor,
            Clock clock) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
        this.chapters = chapters;
        this.extractor = extractor;
        this.clock = clock;
    }

    @Transactional
    public ImportView upload(UUID projectId, UUID userId, MultipartFile file) {
        projectAccess.requireOwnedProject(projectId, userId);
        if (file == null || file.isEmpty()) throw new BadRequestException("import_file_empty", "Import file is empty");
        if (file.getSize() > MAX_BYTES)
            throw new BadRequestException("import_file_too_large", "Import file exceeds 20 MB");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        List<ParsedChapter> parsed;
        try {
            parsed = parse(file.getOriginalFilename(), file.getBytes());
        } catch (IOException exception) {
            throw new BadRequestException("import_file_invalid", "Import file could not be read");
        }
        if (parsed.isEmpty()) throw new BadRequestException("import_no_chapters", "No chapter content was found");
        validateParsedChapters(parsed);
        jdbc.update(
                "INSERT INTO story_import(id,project_id,file_name,media_type,status,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                id,
                projectId,
                safeName(file.getOriginalFilename()),
                file.getContentType(),
                "SPLIT_REVIEW",
                userId,
                now,
                now);
        int sequence = 1;
        for (ParsedChapter chapter : parsed) {
            jdbc.update(
                    "INSERT INTO story_import_chapter(id,import_id,sequence_no,title,content,included) VALUES (?,?,?,?,?,TRUE)",
                    UUID.randomUUID(),
                    id,
                    sequence++,
                    chapter.title(),
                    chapter.content());
        }
        return get(id, userId);
    }

    @Transactional(readOnly = true)
    public List<ImportView> list(UUID projectId, UUID userId) {
        projectAccess.requireOwnedProject(projectId, userId);
        return jdbc.query(
                "SELECT * FROM story_import WHERE project_id=? ORDER BY created_at DESC",
                (rs, row) -> view(
                        rs, importChapters(rs.getObject("id", UUID.class)), candidates(rs.getObject("id", UUID.class))),
                projectId);
    }

    @Transactional(readOnly = true)
    public ImportView get(UUID importId, UUID userId) {
        ImportView value = jdbc.query(
                "SELECT * FROM story_import WHERE id=?",
                rs -> rs.next() ? view(rs, importChapters(importId), candidates(importId)) : null,
                importId);
        if (value == null) throw new NotFoundException("import_not_found", "Import was not found");
        projectAccess.requireOwnedProject(value.projectId(), userId);
        return value;
    }

    @Transactional
    public ImportView replaceChapters(UUID importId, UUID userId, long expectedVersion, List<ChapterInput> values) {
        ImportView current = get(importId, userId);
        requireVersion(current.version(), expectedVersion);
        requireMutable(current.status());
        if (values == null || values.isEmpty())
            throw new BadRequestException("import_chapters_empty", "At least one chapter is required");
        jdbc.update("DELETE FROM story_import_chapter WHERE import_id=?", importId);
        int sequence = 1;
        for (ChapterInput value : values) {
            if (value.title() == null
                    || value.title().isBlank()
                    || value.content() == null
                    || value.content().isBlank()) {
                throw new BadRequestException("import_chapter_invalid", "Chapter title and content are required");
            }
            jdbc.update(
                    "INSERT INTO story_import_chapter(id,import_id,sequence_no,title,content,included) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    importId,
                    sequence++,
                    value.title().trim(),
                    value.content(),
                    value.included());
        }
        touch(importId, "SPLIT_REVIEW");
        return get(importId, userId);
    }

    @Transactional
    public ImportView extract(UUID importId, UUID userId) {
        ImportView current = get(importId, userId);
        requireMutable(current.status());
        jdbc.update(
                "UPDATE story_import SET status='EXTRACTING', error_message=NULL, version=version+1, updated_at=? WHERE id=?",
                clock.instant(),
                importId);
        jdbc.update("DELETE FROM story_import_candidate WHERE import_id=?", importId);
        try {
            for (ImportChapter chapter : importChapters(importId)) {
                if (!chapter.included()) continue;
                ExtractionResult result = extractor.extract(
                        current.projectId(),
                        userId,
                        new AgentInput("Extract canon candidates from imported prose", chapter.content()));
                addCandidates(importId, chapter.sequenceNo(), "EVENT", result.events());
                addCandidates(importId, chapter.sequenceNo(), "FACT", result.candidateFacts());
                addCandidates(importId, chapter.sequenceNo(), "CHARACTER_CHANGE", result.characterChanges());
                addCandidates(importId, chapter.sequenceNo(), "ITEM_TRANSFER", result.itemTransfers());
                addCandidates(importId, chapter.sequenceNo(), "KNOWLEDGE_TRANSFER", result.knowledgeTransfers());
            }
            touch(importId, "CANDIDATE_REVIEW");
        } catch (RuntimeException exception) {
            jdbc.update(
                    "UPDATE story_import SET status='FAILED', error_message=?, version=version+1, updated_at=? WHERE id=?",
                    safeMessage(exception),
                    clock.instant(),
                    importId);
        }
        return get(importId, userId);
    }

    public ImportView retry(UUID importId, UUID userId) {
        return extract(importId, userId);
    }

    @Transactional
    public ImportView cancel(UUID importId, UUID userId) {
        ImportView current = get(importId, userId);
        if ("COMPLETED".equals(current.status()))
            throw new ConflictException("import_completed", "Completed import cannot be cancelled");
        touch(importId, "CANCELLED");
        return get(importId, userId);
    }

    @Transactional
    public ImportView decide(UUID importId, UUID userId, List<CandidateDecision> decisions) {
        ImportView current = get(importId, userId);
        if (!"CANDIDATE_REVIEW".equals(current.status()))
            throw new ConflictException("import_not_in_review", "Import is not waiting for candidate review");
        for (CandidateDecision decision : decisions) {
            int updated = jdbc.update(
                    "UPDATE story_import_candidate SET decision=?, decided_by=?, decided_at=? WHERE id=? AND import_id=?",
                    decision.accepted() ? "ACCEPTED" : "REJECTED",
                    userId,
                    clock.instant(),
                    decision.candidateId(),
                    importId);
            if (updated == 0)
                throw new NotFoundException("import_candidate_not_found", "Import candidate was not found");
        }
        return get(importId, userId);
    }

    @Transactional
    public ImportView complete(UUID importId, UUID userId) {
        ImportView current = get(importId, userId);
        if (!List.of("SPLIT_REVIEW", "CANDIDATE_REVIEW").contains(current.status())) {
            throw new ConflictException("import_not_completable", "Import is not ready to complete");
        }
        List<ImportChapter> source = importChapters(importId);
        Integer maxNo = jdbc.queryForObject(
                "SELECT COALESCE(MAX(chapter_no),0) FROM chapter WHERE project_id=?",
                Integer.class,
                current.projectId());
        int chapterNo = maxNo == null ? 1 : maxNo + 1;
        for (ImportChapter item : source) {
            if (!item.included() || item.createdChapterId() != null) continue;
            var created = chapters.create(current.projectId(), userId, chapterNo++, item.title(), null, null);
            var withVersion = chapters.addVersion(
                    created.chapter().getId(),
                    userId,
                    created.chapter().getVersion(),
                    item.title(),
                    item.content(),
                    null,
                    "Imported from " + current.fileName());
            jdbc.update(
                    "UPDATE story_import_chapter SET created_chapter_id=? WHERE id=?",
                    withVersion.chapter().getId(),
                    item.id());
        }
        touch(importId, "COMPLETED");
        return get(importId, userId);
    }

    @Transactional
    public void mergeAlias(UUID importId, UUID userId, String sourceName, UUID targetCharacterId) {
        ImportView current = get(importId, userId);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM character WHERE id=? AND project_id=?",
                Integer.class,
                targetCharacterId,
                current.projectId());
        if (count == null || count == 0) throw new NotFoundException("character_not_found", "Character was not found");
        jdbc.update(
                "INSERT INTO character_alias_merge(id,project_id,import_id,source_name,target_character_id,created_by,created_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                current.projectId(),
                importId,
                sourceName.trim(),
                targetCharacterId,
                userId,
                clock.instant());
    }

    @Transactional(readOnly = true)
    public byte[] exportGit(UUID projectId, UUID userId) {
        projectAccess.requireOwnedProject(projectId, userId);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                addZip(zip, "README.md", "# StoryWeaver export\n\nProject: " + projectId + "\n");
                List<String[]> chapterRows = jdbc.query(
                        "SELECT c.chapter_no,c.title,cv.content FROM chapter c LEFT JOIN chapter_version cv ON cv.chapter_id=c.id AND cv.version_no=c.current_version_no WHERE c.project_id=? ORDER BY c.chapter_no",
                        (rs, row) -> new String[] {String.valueOf(rs.getInt(1)), rs.getString(2), rs.getString(3)},
                        projectId);
                for (String[] row : chapterRows)
                    addZip(
                            zip,
                            "chapters/%04d-%s.md".formatted(Integer.parseInt(row[0]), slug(row[1])),
                            "# " + row[1] + "\n\n" + (row[2] == null ? "" : row[2]));
                List<String[]> canonRows = jdbc.query(
                        "SELECT ca.name,cav.content FROM canon_asset ca JOIN canon_asset_version cav ON cav.asset_id=ca.id AND cav.version_no=ca.current_version_no WHERE ca.project_id=? ORDER BY ca.name",
                        (rs, row) -> new String[] {rs.getString(1), rs.getString(2)},
                        projectId);
                for (String[] row : canonRows)
                    addZip(zip, "canon/" + slug(row[0]) + ".md", "# " + row[0] + "\n\n" + row[1]);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create export", exception);
        }
    }

    private List<ParsedChapter> parse(String fileName, byte[] bytes) throws IOException {
        String lower = safeName(fileName).toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) return parseZip(bytes);
        String text = lower.endsWith(".docx") ? parseDocx(bytes) : new String(bytes, StandardCharsets.UTF_8);
        if (!lower.endsWith(".txt")
                && !lower.endsWith(".md")
                && !lower.endsWith(".markdown")
                && !lower.endsWith(".docx")) {
            throw new BadRequestException("import_type_unsupported", "Only TXT, Markdown, DOCX and ZIP are supported");
        }
        return split(text, stripExtension(safeName(fileName)));
    }

    private List<ParsedChapter> parseZip(byte[] bytes) throws IOException {
        List<ParsedChapter> result = new ArrayList<>();
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().contains("..")) continue;
                String lower = entry.getName().toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown"))) continue;
                byte[] content = zip.readNBytes((int)
                        Math.min(MAX_BYTES + 1, Math.max(1, entry.getSize() > 0 ? entry.getSize() : MAX_BYTES + 1)));
                if (content.length > MAX_BYTES)
                    throw new BadRequestException("import_file_too_large", "ZIP entry exceeds 20 MB");
                totalBytes += content.length;
                if (totalBytes > MAX_BYTES)
                    throw new BadRequestException("import_archive_too_large", "ZIP contents exceed 20 MB");
                result.addAll(split(new String(content, StandardCharsets.UTF_8), stripExtension(entry.getName())));
                if (result.size() > MAX_CHAPTERS)
                    throw new BadRequestException("import_chapter_limit", "Import contains more than 500 chapters");
            }
        }
        return result;
    }

    private String parseDocx(byte[] bytes) throws IOException {
        byte[] documentXml = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    documentXml = zip.readAllBytes();
                    break;
                }
            }
        }
        if (documentXml == null) throw new BadRequestException("import_docx_invalid", "DOCX document body is missing");
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        StringBuilder result = new StringBuilder();
        try {
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(documentXml));
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.CHARACTERS) result.append(reader.getText());
                else if (event == XMLStreamConstants.END_ELEMENT && "p".equals(reader.getLocalName()))
                    result.append('\n');
                else if (event == XMLStreamConstants.START_ELEMENT && "tab".equals(reader.getLocalName()))
                    result.append('\t');
            }
            reader.close();
            return result.toString();
        } catch (XMLStreamException exception) {
            throw new BadRequestException("import_docx_invalid", "DOCX XML is invalid");
        }
    }

    private List<ParsedChapter> split(String text, String fallbackTitle) {
        String normalized = text.replace("\uFEFF", "").strip();
        if (normalized.isBlank()) return List.of();
        Matcher matcher = CHAPTER_HEADING.matcher(normalized);
        List<Integer> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            titles.add(matcher.group().strip());
        }
        if (starts.isEmpty()) return List.of(new ParsedChapter(fallbackTitle, normalized));
        List<ParsedChapter> result = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int contentStart = normalized.indexOf('\n', starts.get(i));
            int end = i + 1 < starts.size() ? starts.get(i + 1) : normalized.length();
            String content = contentStart < 0
                    ? ""
                    : normalized.substring(contentStart + 1, end).strip();
            if (!content.isBlank()) result.add(new ParsedChapter(titles.get(i), content));
        }
        return result;
    }

    private List<ImportChapter> importChapters(UUID importId) {
        return jdbc.query(
                "SELECT * FROM story_import_chapter WHERE import_id=? ORDER BY sequence_no",
                (rs, row) -> new ImportChapter(
                        rs.getObject("id", UUID.class),
                        rs.getInt("sequence_no"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getBoolean("included"),
                        rs.getObject("created_chapter_id", UUID.class)),
                importId);
    }

    private List<ImportCandidate> candidates(UUID importId) {
        return jdbc.query(
                "SELECT * FROM story_import_candidate WHERE import_id=? ORDER BY source_chapter_no,id",
                (rs, row) -> new ImportCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getString("candidate_type"),
                        rs.getString("content"),
                        (Integer) rs.getObject("source_chapter_no"),
                        rs.getString("decision")),
                importId);
    }

    private ImportView view(ResultSet rs, List<ImportChapter> chapterValues, List<ImportCandidate> candidateValues)
            throws SQLException {
        return new ImportView(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("file_name"),
                rs.getString("media_type"),
                rs.getString("status"),
                rs.getString("error_message"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                chapterValues,
                candidateValues);
    }

    private void addCandidates(UUID importId, int chapterNo, String type, List<String> values) {
        for (String value : values)
            if (value != null && !value.isBlank())
                jdbc.update(
                        "INSERT INTO story_import_candidate(id,import_id,candidate_type,content,source_chapter_no) VALUES (?,?,?,?,?)",
                        UUID.randomUUID(),
                        importId,
                        type,
                        value,
                        chapterNo);
    }

    private void touch(UUID id, String status) {
        jdbc.update(
                "UPDATE story_import SET status=?, error_message=NULL, version=version+1, updated_at=? WHERE id=?",
                status,
                clock.instant(),
                id);
    }

    private void requireMutable(String status) {
        if (List.of("COMPLETED", "CANCELLED", "EXTRACTING").contains(status))
            throw new ConflictException("import_not_mutable", "Import cannot be changed in its current state");
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) throw new ConflictException("stale_version", "Import was changed by another request");
    }

    private String safeMessage(RuntimeException exception) {
        String value = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return value.substring(0, Math.min(500, value.length()));
    }

    private void validateParsedChapters(List<ParsedChapter> parsed) {
        if (parsed.size() > MAX_CHAPTERS)
            throw new BadRequestException("import_chapter_limit", "Import contains more than 500 chapters");
        if (parsed.stream().anyMatch(value -> value.content().length() > MAX_CHAPTER_CHARACTERS))
            throw new BadRequestException("import_chapter_too_large", "A chapter exceeds 500,000 characters");
    }

    private String safeName(String value) {
        return value == null || value.isBlank()
                ? "import.txt"
                : value.replace('\\', '/').substring(value.replace('\\', '/').lastIndexOf('/') + 1);
    }

    private String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private String slug(String value) {
        String result = value.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        return result.isBlank() ? "untitled" : result;
    }

    private void addZip(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private record ParsedChapter(String title, String content) {}

    public record ChapterInput(String title, String content, boolean included) {}

    public record CandidateDecision(UUID candidateId, boolean accepted) {}

    public record ImportChapter(
            UUID id, int sequenceNo, String title, String content, boolean included, UUID createdChapterId) {}

    public record ImportCandidate(
            UUID id, String candidateType, String content, Integer sourceChapterNo, String decision) {}

    public record ImportView(
            UUID id,
            UUID projectId,
            String fileName,
            String mediaType,
            String status,
            String errorMessage,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<ImportChapter> chapters,
            List<ImportCandidate> candidates) {}
}
