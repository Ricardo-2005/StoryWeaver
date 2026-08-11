package com.storyweaver.consistency.application;

import com.storyweaver.character.domain.Character;
import com.storyweaver.character.repository.CharacterRepository;
import com.storyweaver.character.repository.CharacterStateRepository;
import com.storyweaver.consistency.application.ConsistencyModels.CommitProposal;
import com.storyweaver.consistency.application.ConsistencyModels.Issue;
import com.storyweaver.consistency.domain.CharacterKnowledge;
import com.storyweaver.consistency.domain.ItemOwnership;
import com.storyweaver.consistency.domain.ReviewSeverity;
import com.storyweaver.consistency.repository.CharacterKnowledgeRepository;
import com.storyweaver.consistency.repository.ItemOwnershipRepository;
import com.storyweaver.memory.domain.StoryEvent;
import com.storyweaver.memory.repository.StoryEventRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ConsistencyValidatorEngine {
    private final CharacterRepository characters;
    private final CharacterStateRepository states;
    private final ItemOwnershipRepository items;
    private final CharacterKnowledgeRepository knowledge;
    private final StoryEventRepository events;
    private final CharacterStateValidator characterValidator;
    private final ItemOwnershipValidator itemValidator;
    private final TimelineValidator timelineValidator;
    private final KnowledgeBoundaryValidator knowledgeValidator;
    private final CanonReferenceValidator canonValidator;

    public ConsistencyValidatorEngine(
            CharacterRepository characters,
            CharacterStateRepository states,
            ItemOwnershipRepository items,
            CharacterKnowledgeRepository knowledge,
            StoryEventRepository events,
            CharacterStateValidator characterValidator,
            ItemOwnershipValidator itemValidator,
            TimelineValidator timelineValidator,
            KnowledgeBoundaryValidator knowledgeValidator,
            CanonReferenceValidator canonValidator) {
        this.characters = characters;
        this.states = states;
        this.items = items;
        this.knowledge = knowledge;
        this.events = events;
        this.characterValidator = characterValidator;
        this.itemValidator = itemValidator;
        this.timelineValidator = timelineValidator;
        this.knowledgeValidator = knowledgeValidator;
        this.canonValidator = canonValidator;
    }

    public List<Issue> validateDraft(UUID projectId, UUID viewpointCharacterId, String draft) {
        List<Issue> issues = new ArrayList<>();
        for (Character character : characters.findAllByProjectIdOrderByUpdatedAtDesc(projectId)) {
            states.findByCharacterId(character.getId())
                    .ifPresent(state ->
                            issues.addAll(characterValidator.validateDraft(character.getName(), state, draft)));
        }
        for (ItemOwnership item : items.findAllByProjectIdOrderByItemNameAsc(projectId)) {
            issues.addAll(itemValidator.validateDraft(item, draft));
        }
        List<CharacterKnowledge> allKnowledge = knowledge.findAllByProjectIdOrderByUpdatedAtDesc(projectId);
        Set<String> viewpointFacts = allKnowledge.stream()
                .filter(value -> value.getCharacterId().equals(viewpointCharacterId))
                .map(CharacterKnowledge::getFactKey)
                .collect(Collectors.toSet());
        for (CharacterKnowledge value : allKnowledge) {
            if (value.getCharacterId().equals(viewpointCharacterId)) continue;
            issues.addAll(knowledgeValidator.validateDraft(value, viewpointFacts.contains(value.getFactKey()), draft));
        }
        return deduplicate(issues);
    }

    public List<Issue> validateProposal(UUID projectId, CommitProposal proposal) {
        List<Issue> issues = new ArrayList<>();
        Map<UUID, Character> projectCharacters = characters.findAllByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .collect(Collectors.toMap(Character::getId, Function.identity()));
        proposal.characterStateChanges().forEach(change -> {
            if (!projectCharacters.containsKey(change.characterId())) {
                issues.add(missingReference("CHARACTER_STATE", "人物不属于当前项目", change.evidence()));
                return;
            }
            issues.addAll(canonValidator.requireEvidence("CHARACTER_STATE", change.evidence()));
            states.findByCharacterId(change.characterId())
                    .ifPresentOrElse(
                            state -> issues.addAll(characterValidator.validateChange(state, change)),
                            () -> issues.add(missingReference("CHARACTER_STATE", "人物状态不存在", change.evidence())));
        });
        proposal.itemChanges().forEach(change -> {
            if (change.toOwnerCharacterId() != null && !projectCharacters.containsKey(change.toOwnerCharacterId())) {
                issues.add(missingReference("ITEM_OWNERSHIP", "道具接收者不属于当前项目", change.evidence()));
            }
            issues.addAll(canonValidator.requireEvidence("ITEM_OWNERSHIP", change.evidence()));
            ItemOwnership current =
                    items.findByProjectIdAndItemKey(projectId, change.itemKey()).orElse(null);
            if (current == null && change.fromOwnerCharacterId() != null) {
                issues.add(missingReference("ITEM_OWNERSHIP", "尚未出现的道具不能从已有持有者转移", change.evidence()));
            }
            issues.addAll(itemValidator.validateChange(current, change));
        });
        proposal.itemChanges().stream()
                .collect(Collectors.groupingBy(change -> change.itemKey()))
                .forEach((key, changes) -> {
                    long owners = changes.stream()
                            .map(change -> change.toOwnerCharacterId())
                            .distinct()
                            .count();
                    if (owners > 1) {
                        issues.add(new Issue(
                                "ITEM_OWNERSHIP",
                                ReviewSeverity.BLOCKER,
                                "同一道具在一次提交中被分配给多个持有者",
                                key,
                                null,
                                "每次提交只保留一个最终持有者"));
                    }
                });
        String latestTime = latestStoryTime(projectId);
        proposal.timelineEvents().forEach(event -> {
            if (!projectCharacters.keySet().containsAll(event.participantIds())
                    || !projectCharacters.keySet().containsAll(event.knownByIds())) {
                issues.add(missingReference("TIMELINE", "事件人物不属于当前项目", event.evidence()));
            }
            issues.addAll(canonValidator.requireEvidence("TIMELINE", event.evidence()));
            issues.addAll(timelineValidator.validate(latestTime, event));
        });
        Map<String, List<ConsistencyModels.TimelineEvent>> simultaneous = new java.util.LinkedHashMap<>();
        proposal.timelineEvents().forEach(event -> event.participantIds().forEach(participant -> simultaneous
                .computeIfAbsent(participant + "|" + event.storyTime(), ignored -> new ArrayList<>())
                .add(event)));
        simultaneous.forEach((key, values) -> {
            long locations = values.stream()
                    .map(value -> value.location())
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            if (locations > 1) {
                issues.add(new Issue("TIMELINE", ReviewSeverity.BLOCKER, "同一人物在同一故事时间位于多个地点", key, null, "调整事件时间或地点"));
            }
        });
        proposal.knowledgeChanges().forEach(change -> {
            if (!projectCharacters.containsKey(change.characterId())) {
                issues.add(missingReference("KNOWLEDGE_BOUNDARY", "知识接收者不属于当前项目", change.evidence()));
            }
            issues.addAll(canonValidator.requireEvidence("KNOWLEDGE_BOUNDARY", change.evidence()));
            if (change.sourceEventId() != null
                    && events.findById(change.sourceEventId())
                            .filter(event -> event.getProjectId().equals(projectId))
                            .isEmpty()) {
                issues.add(missingReference("KNOWLEDGE_BOUNDARY", "知识来源事件不属于当前项目", change.evidence()));
            }
            issues.addAll(knowledgeValidator.validateChange(change));
        });
        return deduplicate(issues);
    }

    private String latestStoryTime(UUID projectId) {
        return events.findAllByProjectIdOrderByChapterNoDescCreatedAtDesc(projectId).stream()
                .map(StoryEvent::getStoryTime)
                .filter(value -> timelineValidator.date(value) != null)
                .max(Comparator.comparing(value -> LocalDate.parse(value.substring(0, 10))))
                .orElse(null);
    }

    private Issue missingReference(String category, String message, String evidence) {
        return new Issue(category, ReviewSeverity.BLOCKER, message, evidence, null, "仅引用当前项目内的已存在对象");
    }

    private List<Issue> deduplicate(List<Issue> issues) {
        Set<String> seen = new HashSet<>();
        return issues.stream()
                .filter(issue -> seen.add(issue.category() + "|" + issue.message() + "|" + issue.evidence()))
                .toList();
    }
}
