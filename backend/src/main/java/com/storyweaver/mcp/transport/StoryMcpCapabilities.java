package com.storyweaver.mcp.transport;

import com.storyweaver.mcp.application.McpStoryService;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class StoryMcpCapabilities {
    private final McpStoryService story;
    private final McpCurrentUser currentUser;

    public StoryMcpCapabilities(McpStoryService story, McpCurrentUser currentUser) {
        this.story = story;
        this.currentUser = currentUser;
    }

    @McpTool(
            name = "get_character_state",
            description = "Read a character's current runtime state.",
            annotations =
                    @McpTool.McpAnnotations(
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public Map<String, Object> getCharacterState(
            @McpToolParam(description = "Character UUID", required = true) UUID characterId) {
        return story.characterState(currentUser.id(), characterId);
    }

    @McpTool(
            name = "get_character_knowledge",
            description = "Read facts currently known by a character, with evidence.",
            annotations =
                    @McpTool.McpAnnotations(
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public Object getCharacterKnowledge(
            @McpToolParam(description = "Character UUID", required = true) UUID characterId) {
        return story.characterKnowledge(currentUser.id(), characterId);
    }

    @McpTool(
            name = "get_worldbook_entries",
            description = "Read the worldbook entries in an owned project.",
            annotations =
                    @McpTool.McpAnnotations(
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public Object getWorldbookEntries(@McpToolParam(description = "Project UUID", required = true) UUID projectId) {
        return story.worldbookEntries(currentUser.id(), projectId);
    }

    @McpTool(
            name = "get_recent_story_events",
            description = "Read recent structured story events in descending story order.",
            annotations =
                    @McpTool.McpAnnotations(
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public Object getRecentStoryEvents(
            @McpToolParam(description = "Project UUID", required = true) UUID projectId,
            @McpToolParam(description = "Maximum events, 1-100", required = false) Integer limit) {
        return story.recentEvents(currentUser.id(), projectId, limit);
    }

    @McpTool(
            name = "get_item_owner",
            description = "Read the current owner and status of a story item.",
            annotations =
                    @McpTool.McpAnnotations(
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public Object getItemOwner(
            @McpToolParam(description = "Project UUID", required = true) UUID projectId,
            @McpToolParam(description = "Stable item key", required = true) String itemKey) {
        return story.itemOwner(currentUser.id(), projectId, itemKey);
    }

    @McpTool(
            name = "save_candidate_fact",
            description = "Create an evidence-backed CANDIDATE fact. It never changes canon.",
            annotations =
                    @McpTool.McpAnnotations(
                            readOnlyHint = false,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public Map<String, Object> saveCandidateFact(
            @McpToolParam(description = "Project UUID", required = true) UUID projectId,
            @McpToolParam(description = "Stable fact key; generated when omitted", required = false) String factKey,
            @McpToolParam(description = "Candidate fact content", required = true) String content,
            @McpToolParam(description = "Verbatim or precise supporting evidence", required = true) String evidence,
            @McpToolParam(description = "Evidence paragraph key", required = false) String paragraphKey,
            @McpToolParam(description = "Optional idempotency key", required = false) String requestKey) {
        return story.saveCandidate(currentUser.id(), projectId, factKey, content, evidence, paragraphKey, requestKey);
    }

    @McpResource(
            uri = "story://projects/{projectId}/author-intent",
            name = "project-author-intent",
            description = "Author intent and current focus for an owned project.",
            mimeType = "application/json")
    public String authorIntent(String projectId) {
        return story.authorIntent(currentUser.id(), UUID.fromString(projectId));
    }

    @McpResource(
            uri = "story://projects/{projectId}/current-outline",
            name = "project-current-outline",
            description = "Current hierarchical outline for an owned project.",
            mimeType = "application/json")
    public String currentOutline(String projectId) {
        return story.currentOutline(currentUser.id(), UUID.fromString(projectId));
    }

    @McpResource(
            uri = "story://projects/{projectId}/recent-summary",
            name = "project-recent-summary",
            description = "Committed chapter summaries for an owned project.",
            mimeType = "application/json")
    public String recentSummary(String projectId) {
        return story.recentSummary(currentUser.id(), UUID.fromString(projectId));
    }

    @McpResource(
            uri = "story://characters/{characterId}/card",
            name = "character-card",
            description = "Character profile and current state.",
            mimeType = "application/json")
    public String characterCard(String characterId) {
        return story.characterCard(currentUser.id(), UUID.fromString(characterId));
    }

    @McpResource(
            uri = "story://characters/{characterId}/knowledge",
            name = "character-knowledge",
            description = "Facts currently known by the character.",
            mimeType = "application/json")
    public String characterKnowledgeResource(String characterId) {
        return story.characterKnowledgeResource(currentUser.id(), UUID.fromString(characterId));
    }

    @McpPrompt(name = "plan-next-chapter", description = "Plan a chapter from current authoritative story state.")
    public String planNextChapter(
            @McpArg(name = "projectId", description = "Project UUID", required = true) String projectId,
            @McpArg(name = "chapterId", description = "Target chapter UUID", required = true) String chapterId) {
        return story.prompt(
                currentUser.id(), "plan-next-chapter", UUID.fromString(projectId), UUID.fromString(chapterId));
    }

    @McpPrompt(name = "review-chapter", description = "Review a chapter for story consistency.")
    public String reviewChapter(
            @McpArg(name = "projectId", description = "Project UUID", required = true) String projectId,
            @McpArg(name = "chapterId", description = "Target chapter UUID", required = true) String chapterId) {
        return story.prompt(currentUser.id(), "review-chapter", UUID.fromString(projectId), UUID.fromString(chapterId));
    }

    @McpPrompt(name = "query-story-state", description = "Query authoritative story state with read-only tools.")
    public String queryStoryState(
            @McpArg(name = "projectId", description = "Project UUID", required = true) String projectId) {
        return story.prompt(currentUser.id(), "query-story-state", UUID.fromString(projectId), null);
    }
}
