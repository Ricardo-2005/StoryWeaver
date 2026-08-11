package com.storyweaver.mcp.application;

import com.storyweaver.audit.application.McpAuditService;
import com.storyweaver.chapter.application.ChapterService;
import com.storyweaver.character.application.CharacterService;
import com.storyweaver.consistency.application.ConsistencyQueryService;
import com.storyweaver.memory.application.StoryEventService;
import com.storyweaver.outline.application.OutlineService;
import com.storyweaver.project.application.ProjectService;
import com.storyweaver.shared.error.ApiException;
import com.storyweaver.worldbook.application.WorldbookService;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class McpStoryService {
    private final ProjectService projects;
    private final CharacterService characters;
    private final ConsistencyQueryService consistency;
    private final WorldbookService worldbook;
    private final StoryEventService events;
    private final OutlineService outlines;
    private final ChapterService chapters;
    private final McpAuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public McpStoryService(
            ProjectService projects,
            CharacterService characters,
            ConsistencyQueryService consistency,
            WorldbookService worldbook,
            StoryEventService events,
            OutlineService outlines,
            ChapterService chapters,
            McpAuditService audit,
            ObjectMapper json,
            Clock clock) {
        this.projects = projects;
        this.characters = characters;
        this.consistency = consistency;
        this.worldbook = worldbook;
        this.events = events;
        this.outlines = outlines;
        this.chapters = chapters;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    public Map<String, Object> characterState(UUID userId, UUID characterId) {
        return invoke("TOOL", "get_character_state", null, userId, () -> {
            var details = characters.get(characterId, userId);
            var character = details.character();
            var state = details.state();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("projectId", character.getProjectId());
            result.put("characterId", character.getId());
            result.put("name", character.getName());
            result.put("lifeStatus", state.getLifeStatus());
            result.put("currentLocation", state.getCurrentLocation());
            result.put("physicalCondition", state.getPhysicalCondition());
            result.put("emotionalState", state.getEmotionalState());
            result.put("abilities", state.getAbilities());
            result.put("inventoryNotes", state.getInventoryNotes());
            result.put("notes", state.getNotes());
            result.put("version", state.getVersion());
            return result;
        });
    }

    public Object characterKnowledge(UUID userId, UUID characterId) {
        return invoke(
                "TOOL", "get_character_knowledge", null, userId, () -> consistency.knowledge(characterId, userId));
    }

    public Object worldbookEntries(UUID userId, UUID projectId) {
        return invoke("TOOL", "get_worldbook_entries", projectId, userId, () -> worldbook.list(projectId, userId));
    }

    public Object recentEvents(UUID userId, UUID projectId, Integer limit) {
        return invoke("TOOL", "get_recent_story_events", projectId, userId, () -> {
            int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
            return events.list(projectId, userId).stream().limit(safeLimit).toList();
        });
    }

    public Object itemOwner(UUID userId, UUID projectId, String itemKey) {
        return invoke("TOOL", "get_item_owner", projectId, userId, () -> consistency.item(projectId, userId, itemKey));
    }

    public Map<String, Object> saveCandidate(
            UUID userId,
            UUID projectId,
            String factKey,
            String content,
            String evidence,
            String paragraphKey,
            String requestKey) {
        return invoke("TOOL", "save_candidate_fact", projectId, userId, () -> {
            var fact =
                    consistency.saveCandidate(projectId, userId, factKey, content, evidence, paragraphKey, requestKey);
            return Map.of(
                    "id", fact.id(),
                    "projectId", projectId,
                    "factKey", fact.factKey(),
                    "status", fact.status(),
                    "canonEffect", false,
                    "evidence", fact.evidence());
        });
    }

    public String authorIntent(UUID userId, UUID projectId) {
        return resource("author-intent", projectId, userId, () -> {
            var project = projects.get(projectId, userId);
            return json.writeValueAsString(Map.of(
                    "projectId",
                    project.getId(),
                    "name",
                    project.getName(),
                    "authorIntent",
                    project.getAuthorIntent() == null ? "" : project.getAuthorIntent(),
                    "currentFocus",
                    project.getCurrentFocus() == null ? "" : project.getCurrentFocus()));
        });
    }

    public String currentOutline(UUID userId, UUID projectId) {
        return resource(
                "current-outline", projectId, userId, () -> json.writeValueAsString(outlines.list(projectId, userId)));
    }

    public String recentSummary(UUID userId, UUID projectId) {
        return resource("recent-summary", projectId, userId, () -> {
            var values = chapters.list(projectId, userId).stream()
                    .filter(value -> value.currentVersion() != null)
                    .map(value -> Map.of(
                            "chapterId", value.chapter().getId(),
                            "chapterNo", value.chapter().getChapterNo(),
                            "title", value.currentVersion().getTitle(),
                            "summary",
                                    value.currentVersion().getSummary() == null
                                            ? ""
                                            : value.currentVersion().getSummary(),
                            "versionNo", value.currentVersion().getVersionNo()))
                    .toList();
            return json.writeValueAsString(values);
        });
    }

    public String characterCard(UUID userId, UUID characterId) {
        return invoke("RESOURCE", "character-card", null, userId, () -> {
            var details = characters.get(characterId, userId);
            return json.writeValueAsString(Map.of("character", details.character(), "state", details.state()));
        });
    }

    public String characterKnowledgeResource(UUID userId, UUID characterId) {
        return invoke(
                "RESOURCE",
                "character-knowledge",
                null,
                userId,
                () -> json.writeValueAsString(consistency.knowledge(characterId, userId)));
    }

    public String prompt(UUID userId, String name, UUID projectId, UUID chapterId) {
        return invoke("PROMPT", name, projectId, userId, () -> {
            var project = projects.get(projectId, userId);
            String base = "Project: " + project.getName() + "\nAuthor intent: " + project.getAuthorIntent();
            if (chapterId != null) {
                var chapter = chapters.get(chapterId, userId).chapter();
                if (!chapter.getProjectId().equals(projectId)) {
                    throw new com.storyweaver.shared.error.NotFoundException(
                            "chapter_not_found", "Chapter was not found in this project");
                }
                base += "\nChapter " + chapter.getChapterNo() + ": " + chapter.getTitle() + "\nOutline: "
                        + chapter.getOutline();
            }
            return switch (name) {
                case "plan-next-chapter" ->
                    base + "\nPlan the next chapter. Query story state before proposing scenes; do not mutate canon.";
                case "review-chapter" ->
                    base
                            + "\nReview the chapter for continuity, knowledge boundaries, item ownership and timeline issues.";
                case "query-story-state" ->
                    base
                            + "\nUse only read-only StoryWeaver tools to answer the story-state question and cite evidence.";
                default -> throw new IllegalArgumentException("Unknown prompt: " + name);
            };
        });
    }

    private <T> T resource(String name, UUID projectId, UUID userId, Supplier<T> action) {
        return invoke("RESOURCE", name, projectId, userId, action);
    }

    private <T> T invoke(String type, String name, UUID projectId, UUID userId, Supplier<T> action) {
        long started = clock.millis();
        String requestId = UUID.randomUUID().toString();
        try {
            T result = action.get();
            audit.record(userId, projectId, type, name, requestId, "SUCCEEDED", null, clock.millis() - started);
            return result;
        } catch (RuntimeException exception) {
            String code = exception instanceof ApiException api ? api.getCode() : "mcp_operation_failed";
            audit.record(userId, projectId, type, name, requestId, "FAILED", code, clock.millis() - started);
            throw exception;
        }
    }
}
