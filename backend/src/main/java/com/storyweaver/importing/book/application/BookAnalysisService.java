package com.storyweaver.importing.book.application;

import com.storyweaver.importing.book.config.TxtImportProperties;
import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.application.AgentContracts.ExtractionResult;
import com.storyweaver.llm.application.ExtractorGateway;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookAnalysisService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccess;
    private final ExtractorGateway extractor;
    private final ExecutorService executor;
    private final int chunkCharacters;
    private final Clock clock;

    public BookAnalysisService(
            JdbcTemplate jdbc,
            ProjectAccessService projectAccess,
            ExtractorGateway extractor,
            ExecutorService aiTaskExecutor,
            TxtImportProperties properties,
            Clock clock) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
        this.extractor = extractor;
        this.executor = aiTaskExecutor;
        this.chunkCharacters = properties.analysisChunkCharacters();
        this.clock = clock;
    }

    public AnalysisView start(UUID projectId, UUID ownerId, AnalysisRequest request) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        List<Category> categories = request.categories();
        if (categories.isEmpty()) {
            throw new BadRequestException("ANALYSIS_FAILED", "Select at least one analysis category");
        }
        UUID importId = jdbc.query(
                "SELECT id FROM book_import_job WHERE project_id=? AND owner_id=? AND status='COMPLETED' ORDER BY created_at DESC LIMIT 1",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                projectId,
                ownerId);
        if (importId == null) {
            throw new ConflictException("ANALYSIS_FAILED", "TXT import must complete before AI analysis");
        }
        String status =
                jdbc.queryForObject("SELECT analysis_status FROM book_import_job WHERE id=?", String.class, importId);
        if (List.of("QUEUED", "ANALYZING").contains(status)) {
            throw new ConflictException("ANALYSIS_FAILED", "Book analysis is already running");
        }
        jdbc.update("DELETE FROM book_analysis_candidate WHERE import_id=?", importId);
        jdbc.update(
                "UPDATE book_import_job SET analysis_status='QUEUED',analysis_processed_chunks=0,error_code=NULL,error_message=NULL,version=version+1,updated_at=? WHERE id=?",
                timestamp(),
                importId);
        executor.submit(() -> run(importId, projectId, ownerId, categories));
        return get(importId, ownerId);
    }

    @Transactional(readOnly = true)
    public AnalysisView get(UUID importId, UUID ownerId) {
        AnalysisHeader header = requireHeader(importId, ownerId);
        List<AnalysisCandidate> values = jdbc.query(
                "SELECT * FROM book_analysis_candidate WHERE import_id=? ORDER BY chunk_index,candidate_type,created_at",
                (rs, index) -> candidate(rs),
                importId);
        return new AnalysisView(
                importId,
                header.projectId(),
                header.status(),
                header.processedChunks(),
                header.errorCode(),
                header.errorMessage(),
                values);
    }

    @Transactional
    public AnalysisView decide(UUID importId, UUID candidateId, UUID ownerId, boolean accepted) {
        AnalysisHeader header = requireHeader(importId, ownerId);
        if (!"WAITING_REVIEW".equals(header.status())) {
            throw new ConflictException("ANALYSIS_FAILED", "Book analysis is not waiting for candidate review");
        }
        int updated = jdbc.update(
                "UPDATE book_analysis_candidate SET status=? WHERE id=? AND import_id=? AND status='CANDIDATE'",
                accepted ? "ACCEPTED" : "REJECTED",
                candidateId,
                importId);
        if (updated == 0) throw new NotFoundException("ANALYSIS_FAILED", "Analysis candidate was not found");
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_analysis_candidate WHERE import_id=? AND status='CANDIDATE'",
                Integer.class,
                importId);
        if (pending != null && pending == 0) {
            jdbc.update(
                    "UPDATE book_import_job SET analysis_status='COMPLETED',version=version+1,updated_at=? WHERE id=?",
                    timestamp(),
                    importId);
        }
        return get(importId, ownerId);
    }

    private void run(UUID importId, UUID projectId, UUID ownerId, List<Category> categories) {
        jdbc.update(
                "UPDATE book_import_job SET analysis_status='ANALYZING',updated_at=? WHERE id=? AND analysis_status='QUEUED'",
                timestamp(),
                importId);
        try {
            int[] chunkIndex = {0};
            jdbc.query(
                    "SELECT c.id,cv.content FROM book_import_chapter bic JOIN chapter c ON c.id=bic.created_chapter_id JOIN chapter_version cv ON cv.chapter_id=c.id AND cv.version_no=c.current_version_no WHERE bic.import_id=? AND bic.included=TRUE ORDER BY bic.sequence_no",
                    rs -> {
                        while (rs.next()) {
                            UUID chapterId = rs.getObject(1, UUID.class);
                            try (Reader content = rs.getCharacterStream(2)) {
                                try {
                                    for (String chunk : chunks(content)) {
                                        chunkIndex[0]++;
                                        for (Category category : categories) {
                                            analyzeCategory(
                                                    importId,
                                                    projectId,
                                                    ownerId,
                                                    chapterId,
                                                    chunkIndex[0],
                                                    category,
                                                    chunk);
                                        }
                                        jdbc.update(
                                                "UPDATE book_import_job SET analysis_processed_chunks=?,updated_at=? WHERE id=?",
                                                chunkIndex[0],
                                                timestamp(),
                                                importId);
                                    }
                                } catch (IOException exception) {
                                    throw new IllegalStateException("Chapter content stream failed", exception);
                                }
                            } catch (IOException exception) {
                                throw new IllegalStateException("Chapter content stream failed", exception);
                            }
                        }
                        return null;
                    },
                    importId);
            Integer candidates = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM book_analysis_candidate WHERE import_id=?", Integer.class, importId);
            jdbc.update(
                    "UPDATE book_import_job SET analysis_status=?,version=version+1,updated_at=? WHERE id=?",
                    candidates != null && candidates > 0 ? "WAITING_REVIEW" : "COMPLETED",
                    timestamp(),
                    importId);
        } catch (RuntimeException exception) {
            jdbc.update(
                    "UPDATE book_import_job SET analysis_status='FAILED',error_code='ANALYSIS_FAILED',error_message=?,version=version+1,updated_at=? WHERE id=?",
                    safeMessage(exception),
                    timestamp(),
                    importId);
        }
    }

    private void analyzeCategory(
            UUID importId,
            UUID projectId,
            UUID ownerId,
            UUID chapterId,
            int chunkIndex,
            Category category,
            String chunk) {
        ExtractionResult result = extractor.extract(projectId, ownerId, new AgentInput(category.instruction(), chunk));
        List<String> outputs =
                switch (category) {
                    case CHARACTER -> result.characterChanges();
                    case WORLDBOOK -> result.candidateFacts();
                    case OUTLINE -> List.of(result.summary());
                    case EVENT -> result.events();
                    case SKILL -> result.candidateFacts();
                };
        for (String output : outputs) {
            if (output == null || output.isBlank()) continue;
            jdbc.update(
                    "INSERT INTO book_analysis_candidate(id,import_id,project_id,chapter_id,chunk_index,candidate_type,content,status,created_at) VALUES (?,?,?,?,?,?,?,'CANDIDATE',?)",
                    UUID.randomUUID(),
                    importId,
                    projectId,
                    chapterId,
                    chunkIndex,
                    category.name(),
                    output,
                    timestamp());
        }
    }

    private List<String> chunks(Reader source) throws IOException {
        List<String> result = new ArrayList<>();
        BufferedReader reader = source instanceof BufferedReader value ? value : new BufferedReader(source, 32 * 1024);
        StringBuilder current = new StringBuilder(chunkCharacters);
        String line;
        while ((line = reader.readLine()) != null) {
            String remaining = line + '\n';
            while (!remaining.isEmpty()) {
                int capacity = chunkCharacters - current.length();
                if (capacity == 0) {
                    result.add(current.toString());
                    current.setLength(0);
                    capacity = chunkCharacters;
                }
                int take = Math.min(capacity, remaining.length());
                current.append(remaining, 0, take);
                remaining = remaining.substring(take);
            }
            if (current.length() >= chunkCharacters * 3 / 4) {
                result.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private AnalysisHeader requireHeader(UUID importId, UUID ownerId) {
        AnalysisHeader value = jdbc.query(
                "SELECT project_id,analysis_status,analysis_processed_chunks,error_code,error_message FROM book_import_job WHERE id=? AND owner_id=?",
                rs -> rs.next()
                        ? new AnalysisHeader(
                                rs.getObject("project_id", UUID.class),
                                rs.getString("analysis_status"),
                                rs.getInt("analysis_processed_chunks"),
                                rs.getString("error_code"),
                                rs.getString("error_message"))
                        : null,
                importId,
                ownerId);
        if (value == null) throw new NotFoundException("IMPORT_NOT_FOUND", "TXT import was not found");
        return value;
    }

    private AnalysisCandidate candidate(ResultSet rs) throws SQLException {
        return new AnalysisCandidate(
                rs.getObject("id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getInt("chunk_index"),
                rs.getString("candidate_type"),
                rs.getString("content"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toInstant());
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return value.substring(0, Math.min(500, value.length()));
    }

    private Timestamp timestamp() {
        return Timestamp.from(clock.instant());
    }

    public enum Category {
        CHARACTER(
                "Extract only character candidates from this chapter chunk. Put candidates in characterChanges. Do not confirm canon."),
        WORLDBOOK(
                "Extract only worldbook candidates from this chapter chunk. Put candidates in candidateFacts. Do not confirm canon."),
        OUTLINE(
                "Summarize only the retrospective outline represented by this chapter chunk. Do not invent future plot."),
        EVENT("Extract only event candidates from this chapter chunk. Put candidates in events. Do not confirm canon."),
        SKILL(
                "Extract only reusable writing-skill candidates evidenced by this chapter chunk. Put candidates in candidateFacts. Do not confirm canon.");

        private final String instruction;

        Category(String instruction) {
            this.instruction = instruction;
        }

        public String instruction() {
            return instruction;
        }
    }

    public record AnalysisRequest(
            boolean extractCharacters,
            boolean extractWorldbook,
            boolean extractOutline,
            boolean extractEvents,
            boolean extractSkills) {
        List<Category> categories() {
            List<Category> result = new ArrayList<>();
            if (extractCharacters) result.add(Category.CHARACTER);
            if (extractWorldbook) result.add(Category.WORLDBOOK);
            if (extractOutline) result.add(Category.OUTLINE);
            if (extractEvents) result.add(Category.EVENT);
            if (extractSkills) result.add(Category.SKILL);
            return List.copyOf(result);
        }
    }

    public record AnalysisCandidate(
            UUID id,
            UUID chapterId,
            int chunkIndex,
            String candidateType,
            String content,
            String status,
            Instant createdAt) {}

    public record AnalysisView(
            UUID importId,
            UUID projectId,
            String status,
            int processedChunks,
            String errorCode,
            String errorMessage,
            List<AnalysisCandidate> candidates) {}

    private record AnalysisHeader(
            UUID projectId, String status, int processedChunks, String errorCode, String errorMessage) {}
}
