package com.storyweaver.consistency.api;

import com.storyweaver.consistency.domain.FactStatus;
import com.storyweaver.consistency.domain.ItemStatus;
import com.storyweaver.consistency.domain.KnowledgeCertainty;
import java.time.Instant;
import java.util.UUID;

public final class ConsistencyDtos {
    private ConsistencyDtos() {}

    public record FactResponse(
            UUID id,
            UUID chapterId,
            String factKey,
            String content,
            String evidence,
            String paragraphKey,
            FactStatus status,
            Instant createdAt) {}

    public record ItemOwnershipResponse(
            UUID id,
            String itemKey,
            String itemName,
            UUID ownerCharacterId,
            ItemStatus status,
            UUID acquiredChapterId,
            String evidence,
            long version) {}

    public record CharacterKnowledgeResponse(
            UUID id,
            UUID characterId,
            String factKey,
            String content,
            KnowledgeCertainty certainty,
            UUID sourceEventId,
            UUID acquiredChapterId,
            String evidence,
            long version) {}
}
