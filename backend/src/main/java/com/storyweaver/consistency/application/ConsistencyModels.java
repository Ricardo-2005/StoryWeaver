package com.storyweaver.consistency.application;

import com.storyweaver.character.domain.LifeStatus;
import com.storyweaver.consistency.domain.ItemStatus;
import com.storyweaver.consistency.domain.KnowledgeCertainty;
import com.storyweaver.consistency.domain.ReviewSeverity;
import java.util.List;
import java.util.UUID;

public final class ConsistencyModels {
    private ConsistencyModels() {}

    public record Issue(
            String category,
            ReviewSeverity severity,
            String message,
            String evidence,
            String historicalEvidence,
            String suggestion) {
        public boolean blocking() {
            return severity == ReviewSeverity.BLOCKER;
        }
    }

    public record CharacterStateChange(
            UUID characterId,
            LifeStatus lifeStatus,
            String currentLocation,
            String physicalCondition,
            String emotionalState,
            String abilities,
            String inventoryNotes,
            String notes,
            long expectedVersion,
            String evidence) {}

    public record ItemChange(
            String itemKey,
            String itemName,
            UUID fromOwnerCharacterId,
            UUID toOwnerCharacterId,
            ItemStatus status,
            String evidence) {}

    public record TimelineEvent(
            List<UUID> participantIds,
            List<UUID> knownByIds,
            String location,
            String storyTime,
            String action,
            String result,
            double importance,
            String evidence) {}

    public record KnowledgeChange(
            UUID characterId,
            String factKey,
            String content,
            KnowledgeCertainty certainty,
            UUID sourceEventId,
            String evidence) {}

    public record CommitProposal(
            List<Integer> acceptedFactIndexes,
            List<CharacterStateChange> characterStateChanges,
            List<ItemChange> itemChanges,
            List<TimelineEvent> timelineEvents,
            List<KnowledgeChange> knowledgeChanges) {}
}
