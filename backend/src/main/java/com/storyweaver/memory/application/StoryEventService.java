package com.storyweaver.memory.application;

import com.storyweaver.chapter.domain.Chapter;
import com.storyweaver.chapter.repository.ChapterRepository;
import com.storyweaver.character.repository.CharacterRepository;
import com.storyweaver.llm.application.EmbeddingGateway;
import com.storyweaver.llm.application.EmbeddingGateway.EmbeddingResult;
import com.storyweaver.llm.config.RetrievalProperties;
import com.storyweaver.llm.domain.EmbeddingStatus;
import com.storyweaver.memory.domain.StoryEvent;
import com.storyweaver.memory.domain.StoryEvent.EventValues;
import com.storyweaver.memory.repository.StoryEventRepository;
import com.storyweaver.memory.repository.StoryEventVectorRepository;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryEventService {
    private final StoryEventRepository events;
    private final StoryEventVectorRepository vectors;
    private final ChapterRepository chapters;
    private final CharacterRepository characters;
    private final ProjectAccessService projectAccess;
    private final EmbeddingGateway embeddings;
    private final RetrievalProperties retrieval;
    private final MeterRegistry meters;
    private final Clock clock;

    public StoryEventService(
            StoryEventRepository events,
            StoryEventVectorRepository vectors,
            ChapterRepository chapters,
            CharacterRepository characters,
            ProjectAccessService projectAccess,
            EmbeddingGateway embeddings,
            RetrievalProperties retrieval,
            MeterRegistry meters,
            Clock clock) {
        this.events = events;
        this.vectors = vectors;
        this.chapters = chapters;
        this.characters = characters;
        this.projectAccess = projectAccess;
        this.embeddings = embeddings;
        this.retrieval = retrieval;
        this.meters = meters;
        this.clock = clock;
    }

    @Transactional
    public StoryEvent create(UUID projectId, UUID ownerId, Input input) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        EventValues values = values(projectId, input);
        StoryEvent event = new StoryEvent(projectId, values, clock.instant());
        float[] vector = applyEmbedding(event);
        events.saveAndFlush(event);
        persistVector(event, vector);
        return event;
    }

    @Transactional(readOnly = true)
    public List<StoryEvent> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return events.findAllByProjectIdOrderByChapterNoDescCreatedAtDesc(projectId);
    }

    @Transactional
    public StoryEvent update(UUID eventId, UUID ownerId, long expectedVersion, Input input) {
        StoryEvent event = requireOwnedEvent(eventId, ownerId);
        if (event.getVersion() != expectedVersion) {
            throw new ConflictException("optimistic_lock_conflict", "The story event changed; reload it first");
        }
        event.revise(values(event.getProjectId(), input), clock.instant());
        float[] vector = applyEmbedding(event);
        events.flush();
        persistVector(event, vector);
        return event;
    }

    @Transactional(readOnly = true)
    public SearchResult search(UUID projectId, UUID ownerId, SearchInput input) {
        long started = clock.millis();
        projectAccess.requireOwnedProject(projectId, ownerId);
        validateCharacters(projectId, input.participantIds());
        int topK = input.topK() == null ? retrieval.eventDefaultTopK() : input.topK();
        EmbeddingResult queryEmbedding = embeddings.embed(input.query());
        Map<UUID, Double> semanticScores = new HashMap<>();
        if (queryEmbedding.available()) {
            vectors.search(projectId, queryEmbedding.vector(), Math.min(100, topK * 4))
                    .forEach(match -> semanticScores.put(match.eventId(), match.similarity()));
        }
        Set<UUID> requestedParticipants = new LinkedHashSet<>(input.participantIds());
        List<ScoredEvent> scored = events.findAllByProjectIdOrderByChapterNoDescCreatedAtDesc(projectId).stream()
                .map(event ->
                        score(event, input, requestedParticipants, semanticScores.getOrDefault(event.getId(), 0.0)))
                .sorted(Comparator.comparingDouble(ScoredEvent::score)
                        .reversed()
                        .thenComparing(result -> result.event().getCreatedAt(), Comparator.reverseOrder()))
                .limit(topK)
                .toList();
        meters.timer(
                        "storyweaver.memory.search.latency",
                        "status",
                        queryEmbedding.available() ? "AVAILABLE" : "DEGRADED")
                .record(Duration.ofMillis(clock.millis() - started));
        return new SearchResult(
                queryEmbedding.available(), queryEmbedding.unavailableReason(), input.query(), List.copyOf(scored));
    }

    private ScoredEvent score(StoryEvent event, SearchInput input, Set<UUID> participants, double semanticSimilarity) {
        Set<String> reasons = new LinkedHashSet<>();
        double participantScore = overlap(participants, event.getParticipantIds());
        double locationScore = input.location() != null
                        && event.getLocation() != null
                        && input.location().trim().equalsIgnoreCase(event.getLocation())
                ? 1.0
                : 0.0;
        double chapterScore = input.chapterNo() != null && event.getChapterNo() != null
                ? 1.0 / (1.0 + Math.abs(input.chapterNo() - event.getChapterNo()))
                : 0.0;
        if (semanticSimilarity > 0) reasons.add(String.format(Locale.ROOT, "SEMANTIC:%.4f", semanticSimilarity));
        if (participantScore > 0) reasons.add("PARTICIPANT_OVERLAP");
        if (locationScore > 0) reasons.add("LOCATION");
        if (chapterScore > 0) reasons.add(String.format(Locale.ROOT, "CHAPTER_PROXIMITY:%.4f", chapterScore));
        reasons.add(String.format(Locale.ROOT, "IMPORTANCE:%.4f", event.getImportance()));
        double score = retrieval.semanticWeight() * semanticSimilarity
                + retrieval.participantWeight() * participantScore
                + retrieval.locationWeight() * locationScore
                + retrieval.chapterProximityWeight() * chapterScore
                + retrieval.importanceWeight() * event.getImportance();
        return new ScoredEvent(
                event, score, semanticSimilarity, participantScore, locationScore, chapterScore, reasons);
    }

    private double overlap(Set<UUID> requested, UUID[] actual) {
        if (requested.isEmpty()) return 0.0;
        long matches = Arrays.stream(actual).filter(requested::contains).count();
        return (double) matches / requested.size();
    }

    private EventValues values(UUID projectId, Input input) {
        Chapter chapter = null;
        if (input.chapterId() != null) {
            chapter = chapters.findById(input.chapterId())
                    .filter(candidate -> candidate.getProjectId().equals(projectId))
                    .orElseThrow(
                            () -> new NotFoundException("chapter_not_found", "Chapter was not found in this project"));
        }
        validateCharacters(projectId, input.participantIds());
        validateCharacters(projectId, input.knownByIds());
        return new EventValues(
                chapter == null ? null : chapter.getId(),
                chapter == null ? null : chapter.getChapterNo(),
                distinct(input.participantIds()),
                distinct(input.knownByIds()),
                nullable(input.location()),
                nullable(input.storyTime()),
                input.action().trim(),
                input.result().trim(),
                input.importance(),
                nullable(input.evidenceParagraph()));
    }

    private void validateCharacters(UUID projectId, List<UUID> ids) {
        for (UUID id : ids) {
            if (characters
                    .findById(id)
                    .filter(character -> character.getProjectId().equals(projectId))
                    .isEmpty()) {
                throw new NotFoundException("character_not_found", "Character was not found in this project");
            }
        }
    }

    private float[] applyEmbedding(StoryEvent event) {
        EmbeddingResult result = embeddings.embed(embeddingText(event));
        event.embedding(
                result.available() ? EmbeddingStatus.AVAILABLE : EmbeddingStatus.UNAVAILABLE,
                result.model(),
                clock.instant());
        return result.available() ? result.vector() : null;
    }

    private void persistVector(StoryEvent event, float[] vector) {
        if (event.getEmbeddingStatus() == EmbeddingStatus.AVAILABLE && vector != null) {
            try {
                vectors.write(event.getId(), vector);
            } catch (RuntimeException exception) {
                event.embedding(EmbeddingStatus.UNAVAILABLE, event.getEmbeddingModel(), clock.instant());
                vectors.clear(event.getId());
                events.flush();
            }
        } else {
            vectors.clear(event.getId());
        }
    }

    private String embeddingText(StoryEvent event) {
        return String.join(
                "\n",
                event.getStoryTime() == null ? "" : event.getStoryTime(),
                event.getLocation() == null ? "" : event.getLocation(),
                event.getAction(),
                event.getResult());
    }

    private StoryEvent requireOwnedEvent(UUID eventId, UUID ownerId) {
        StoryEvent event = events.findById(eventId)
                .orElseThrow(() -> new NotFoundException("story_event_not_found", "Story event was not found"));
        projectAccess.requireOwnedProject(event.getProjectId(), ownerId);
        return event;
    }

    private UUID[] distinct(List<UUID> ids) {
        return ids.stream().distinct().toArray(UUID[]::new);
    }

    private String nullable(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public record Input(
            UUID chapterId,
            List<UUID> participantIds,
            List<UUID> knownByIds,
            String location,
            String storyTime,
            String action,
            String result,
            double importance,
            String evidenceParagraph) {}

    public record SearchInput(
            String query, List<UUID> participantIds, String location, Integer chapterNo, Integer topK) {}

    public record ScoredEvent(
            StoryEvent event,
            double score,
            double semanticSimilarity,
            double participantScore,
            double locationScore,
            double chapterProximityScore,
            Set<String> reasons) {}

    public record SearchResult(
            boolean embeddingAvailable, String degradedReason, String query, List<ScoredEvent> matches) {}
}
