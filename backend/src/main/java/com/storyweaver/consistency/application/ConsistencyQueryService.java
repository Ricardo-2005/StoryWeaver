package com.storyweaver.consistency.application;

import com.storyweaver.character.repository.CharacterRepository;
import com.storyweaver.consistency.api.ConsistencyDtos.CharacterKnowledgeResponse;
import com.storyweaver.consistency.api.ConsistencyDtos.FactResponse;
import com.storyweaver.consistency.api.ConsistencyDtos.ItemOwnershipResponse;
import com.storyweaver.consistency.domain.FactStatus;
import com.storyweaver.consistency.domain.StoryFact;
import com.storyweaver.consistency.repository.CharacterKnowledgeRepository;
import com.storyweaver.consistency.repository.ItemOwnershipRepository;
import com.storyweaver.consistency.repository.StoryFactRepository;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsistencyQueryService {
    private final StoryFactRepository facts;
    private final ItemOwnershipRepository items;
    private final CharacterKnowledgeRepository knowledge;
    private final CharacterRepository characters;
    private final ProjectAccessService projectAccess;
    private final Clock clock;

    public ConsistencyQueryService(
            StoryFactRepository facts,
            ItemOwnershipRepository items,
            CharacterKnowledgeRepository knowledge,
            CharacterRepository characters,
            ProjectAccessService projectAccess,
            Clock clock) {
        this.facts = facts;
        this.items = items;
        this.knowledge = knowledge;
        this.characters = characters;
        this.projectAccess = projectAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FactResponse> facts(UUID projectId, UUID userId, FactStatus status) {
        projectAccess.requireOwnedProject(projectId, userId);
        return facts.findAllByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status).stream()
                .map(value -> new FactResponse(
                        value.getId(),
                        value.getChapterId(),
                        value.getFactKey(),
                        value.getContent(),
                        value.getEvidence(),
                        value.getParagraphKey(),
                        value.getStatus(),
                        value.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ItemOwnershipResponse> items(UUID projectId, UUID userId) {
        projectAccess.requireOwnedProject(projectId, userId);
        return items.findAllByProjectIdOrderByItemNameAsc(projectId).stream()
                .map(value -> new ItemOwnershipResponse(
                        value.getId(),
                        value.getItemKey(),
                        value.getItemName(),
                        value.getOwnerCharacterId(),
                        value.getItemStatus(),
                        value.getAcquiredChapterId(),
                        value.getEvidence(),
                        value.getVersion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ItemOwnershipResponse item(UUID projectId, UUID userId, String itemKey) {
        projectAccess.requireOwnedProject(projectId, userId);
        var value = items.findByProjectIdAndItemKey(projectId, itemKey.strip())
                .orElseThrow(() -> new NotFoundException("item_not_found", "Item ownership was not found"));
        return new ItemOwnershipResponse(
                value.getId(),
                value.getItemKey(),
                value.getItemName(),
                value.getOwnerCharacterId(),
                value.getItemStatus(),
                value.getAcquiredChapterId(),
                value.getEvidence(),
                value.getVersion());
    }

    @Transactional
    public FactResponse saveCandidate(
            UUID projectId,
            UUID userId,
            String factKey,
            String content,
            String evidence,
            String paragraphKey,
            String requestKey) {
        projectAccess.requireOwnedProject(projectId, userId);
        if (content == null || content.isBlank()) {
            throw new BadRequestException("candidate_content_required", "Candidate fact content is required");
        }
        if (evidence == null || evidence.isBlank()) {
            throw new BadRequestException("candidate_evidence_required", "Candidate fact evidence is required");
        }
        if (requestKey != null && !requestKey.isBlank()) {
            var existing = facts.findByProjectIdAndCreatedByAndMcpRequestKey(projectId, userId, requestKey.strip());
            if (existing.isPresent()) return factResponse(existing.get());
        }
        String normalizedKey = factKey == null || factKey.isBlank()
                ? UUID.nameUUIDFromBytes(content.strip().getBytes(StandardCharsets.UTF_8))
                        .toString()
                : factKey.strip();
        StoryFact value = facts.save(StoryFact.mcpCandidate(
                projectId,
                userId,
                normalizedKey,
                content.strip(),
                evidence.strip(),
                paragraphKey == null || paragraphKey.isBlank() ? "mcp-evidence" : paragraphKey.strip(),
                requestKey == null || requestKey.isBlank() ? null : requestKey.strip(),
                clock.instant()));
        return factResponse(value);
    }

    @Transactional(readOnly = true)
    public List<CharacterKnowledgeResponse> knowledge(UUID characterId, UUID userId) {
        var character = characters
                .findById(characterId)
                .orElseThrow(() -> new NotFoundException("character_not_found", "Character was not found"));
        projectAccess.requireOwnedProject(character.getProjectId(), userId);
        return knowledge
                .findAllByProjectIdAndCharacterIdOrderByUpdatedAtDesc(character.getProjectId(), characterId)
                .stream()
                .map(value -> new CharacterKnowledgeResponse(
                        value.getId(),
                        value.getCharacterId(),
                        value.getFactKey(),
                        value.getContent(),
                        value.getCertainty(),
                        value.getSourceEventId(),
                        value.getAcquiredChapterId(),
                        value.getEvidence(),
                        value.getVersion()))
                .toList();
    }

    private FactResponse factResponse(StoryFact value) {
        return new FactResponse(
                value.getId(),
                value.getChapterId(),
                value.getFactKey(),
                value.getContent(),
                value.getEvidence(),
                value.getParagraphKey(),
                value.getStatus(),
                value.getCreatedAt());
    }
}
