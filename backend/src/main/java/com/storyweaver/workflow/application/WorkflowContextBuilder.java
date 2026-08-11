package com.storyweaver.workflow.application;

import com.storyweaver.canon.application.CanonSnapshotContributor;
import com.storyweaver.chapter.domain.Chapter;
import com.storyweaver.chapter.domain.ChapterVersion;
import com.storyweaver.chapter.repository.ChapterRepository;
import com.storyweaver.chapter.repository.ChapterVersionRepository;
import com.storyweaver.character.domain.Character;
import com.storyweaver.character.repository.CharacterRepository;
import com.storyweaver.character.repository.CharacterStateRepository;
import com.storyweaver.consistency.domain.FactStatus;
import com.storyweaver.consistency.repository.CharacterKnowledgeRepository;
import com.storyweaver.consistency.repository.ItemOwnershipRepository;
import com.storyweaver.consistency.repository.StoryFactRepository;
import com.storyweaver.memory.application.StoryEventService;
import com.storyweaver.memory.application.StoryEventService.SearchInput;
import com.storyweaver.project.domain.NovelProject;
import com.storyweaver.project.repository.NovelProjectRepository;
import com.storyweaver.skill.application.SkillService;
import com.storyweaver.workflow.config.WorkflowProperties;
import com.storyweaver.workflow.domain.ContextPacket;
import com.storyweaver.workflow.domain.WorkflowRun;
import com.storyweaver.worldbook.application.TokenEstimator;
import com.storyweaver.worldbook.application.WorldbookService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class WorkflowContextBuilder {
    private final NovelProjectRepository projects;
    private final ChapterRepository chapters;
    private final ChapterVersionRepository chapterVersions;
    private final CharacterRepository characters;
    private final CharacterStateRepository characterStates;
    private final StoryFactRepository facts;
    private final ItemOwnershipRepository items;
    private final CharacterKnowledgeRepository knowledge;
    private final CanonSnapshotContributor canon;
    private final SkillService skills;
    private final WorldbookService worldbook;
    private final StoryEventService memory;
    private final TokenEstimator tokens;
    private final WorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ObservationRegistry observations;

    public WorkflowContextBuilder(
            NovelProjectRepository projects,
            ChapterRepository chapters,
            ChapterVersionRepository chapterVersions,
            CharacterRepository characters,
            CharacterStateRepository characterStates,
            StoryFactRepository facts,
            ItemOwnershipRepository items,
            CharacterKnowledgeRepository knowledge,
            CanonSnapshotContributor canon,
            SkillService skills,
            WorldbookService worldbook,
            StoryEventService memory,
            TokenEstimator tokens,
            WorkflowProperties properties,
            ObjectMapper objectMapper,
            ObservationRegistry observations,
            Clock clock) {
        this.projects = projects;
        this.chapters = chapters;
        this.chapterVersions = chapterVersions;
        this.characters = characters;
        this.characterStates = characterStates;
        this.facts = facts;
        this.items = items;
        this.knowledge = knowledge;
        this.canon = canon;
        this.skills = skills;
        this.worldbook = worldbook;
        this.memory = memory;
        this.tokens = tokens;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.observations = observations;
        this.clock = clock;
    }

    public ContextPacket build(WorkflowRun run) {
        NovelProject project = projects.findById(run.getProjectId()).orElseThrow();
        Chapter chapter = chapters.findById(run.getChapterId()).orElseThrow();
        Character viewpoint = characters.findById(run.getViewpointCharacterId()).orElseThrow();
        String query = String.join(
                "\n",
                chapter.getOutline(),
                project.getCurrentFocus() == null ? "" : project.getCurrentFocus(),
                run.getInstruction());
        var worldbookPreview = Observation.createNotStarted("storyweaver.workflow.worldbook", observations)
                .lowCardinalityKeyValue("step", "WORLDBOOK")
                .observe(() -> worldbook.preview(
                        run.getProjectId(), run.getUserId(), query, chapter.getId(), viewpoint.getId(), null, null));
        var memorySearch = Observation.createNotStarted("storyweaver.workflow.memory", observations)
                .lowCardinalityKeyValue("step", "MEMORY")
                .observe(() -> memory.search(
                        run.getProjectId(),
                        run.getUserId(),
                        new SearchInput(query, List.of(viewpoint.getId()), null, chapter.getChapterNo(), null)));
        var skillComposition = skills.compose(run.getProjectId(), run.getUserId(), chapter.getId());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("project", project(project));
        context.put("chapter", chapter(chapter));
        context.put("viewpointCharacter", character(viewpoint));
        context.put("previousChapter", previousChapter(run.getProjectId(), chapter.getChapterNo()));
        context.put("canonAssets", canon.contribute(run.getProjectId()));
        context.put(
                "acceptedFacts",
                facts.findAllByProjectIdAndStatusOrderByCreatedAtDesc(run.getProjectId(), FactStatus.ACCEPTED));
        context.put("itemOwnership", items.findAllByProjectIdOrderByItemNameAsc(run.getProjectId()));
        context.put("characterKnowledge", knowledge.findAllByProjectIdOrderByUpdatedAtDesc(run.getProjectId()));
        context.put("instruction", run.getInstruction());
        Map<String, Object> worldbookReport = map(worldbookPreview);
        Map<String, Object> memoryReport = map(memorySearch);
        Map<String, Object> skillSnapshot = map(skillComposition);
        String rendered = objectMapper.writeValueAsString(Map.of(
                "context", context, "worldbook", worldbookReport, "memory", memoryReport, "skills", skillSnapshot));
        int tokenEstimate = tokens.estimate("", rendered);
        Instant now = clock.instant();
        return new ContextPacket(
                run.getProjectId(),
                run.getChapterId(),
                run.getId(),
                run.getUserId(),
                context,
                worldbookReport,
                memoryReport,
                skillSnapshot,
                tokenEstimate,
                BigDecimal.ZERO,
                now.plus(properties.contextTtl()),
                now);
    }

    private Map<String, Object> project(NovelProject project) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", project.getId());
        value.put("name", project.getName());
        value.put("genre", project.getGenre());
        value.put("customGenre", project.getCustomGenre());
        value.put("targetAudience", project.getTargetAudience());
        value.put("narrativePerspective", project.getNarrativePerspective());
        value.put("lengthType", project.getLengthType());
        value.put("premise", project.getPremise());
        value.put("description", project.getDescription());
        value.put("authorIntent", project.getAuthorIntent());
        value.put("currentFocus", project.getCurrentFocus());
        value.put("worldRules", project.getWorldRules());
        value.put("targetWordCount", project.getTargetWordCount());
        value.put("chapterWordTarget", project.getChapterWordTarget());
        value.put("version", project.getVersion());
        return value;
    }

    private Map<String, Object> chapter(Chapter chapter) {
        return Map.of(
                "id", chapter.getId(),
                "chapterNo", chapter.getChapterNo(),
                "title", chapter.getTitle(),
                "outline", chapter.getOutline(),
                "version", chapter.getVersion());
    }

    private Map<String, Object> character(Character character) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", character.getId());
        value.put("name", character.getName());
        value.put("role", character.getRole());
        value.put("description", character.getDescription());
        value.put("personality", character.getPersonality());
        value.put("background", character.getBackground());
        value.put("goals", character.getGoals());
        value.put("version", character.getVersion());
        characterStates.findByCharacterId(character.getId()).ifPresent(state -> {
            Map<String, Object> stateValue = new LinkedHashMap<>();
            stateValue.put("lifeStatus", state.getLifeStatus());
            stateValue.put("currentLocation", state.getCurrentLocation());
            stateValue.put("physicalCondition", state.getPhysicalCondition());
            stateValue.put("emotionalState", state.getEmotionalState());
            stateValue.put("abilities", state.getAbilities());
            stateValue.put("inventoryNotes", state.getInventoryNotes());
            stateValue.put("notes", state.getNotes());
            stateValue.put("version", state.getVersion());
            value.put("state", stateValue);
        });
        return value;
    }

    private Map<String, Object> previousChapter(java.util.UUID projectId, int chapterNo) {
        if (chapterNo <= 1) return Map.of();
        Chapter previous = chapters.findAllByProjectIdOrderByChapterNoAsc(projectId).stream()
                .filter(candidate -> candidate.getChapterNo() == chapterNo - 1)
                .findFirst()
                .orElse(null);
        if (previous == null || previous.getCurrentVersionNo() == 0) return Map.of();
        ChapterVersion version = chapterVersions
                .findByChapterIdAndVersionNo(previous.getId(), previous.getCurrentVersionNo())
                .orElseThrow();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("chapterId", previous.getId());
        value.put("chapterNo", previous.getChapterNo());
        value.put("title", version.getTitle());
        value.put("summary", version.getSummary());
        value.put("versionNo", version.getVersionNo());
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }
}
