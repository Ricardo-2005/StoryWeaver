package com.storyweaver.consistency.application;

import com.storyweaver.consistency.application.ConsistencyModels.Issue;
import com.storyweaver.consistency.application.ConsistencyModels.KnowledgeChange;
import com.storyweaver.consistency.domain.CharacterKnowledge;
import com.storyweaver.consistency.domain.KnowledgeCertainty;
import com.storyweaver.consistency.domain.ReviewSeverity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBoundaryValidator {
    public List<Issue> validateDraft(CharacterKnowledge knowledge, boolean viewpointKnows, String draft) {
        if (viewpointKnows || !draft.contains(knowledge.getContent())) return List.of();
        if (containsAny(draft, "得知", "告诉", "听见", "收到", "发现", "猜测", "怀疑")) return List.of();
        return List.of(new Issue(
                "KNOWLEDGE_BOUNDARY",
                ReviewSeverity.BLOCKER,
                "视角人物直接使用了其尚未知晓的秘密",
                knowledge.getContent(),
                "该知识只属于其他人物",
                "补充传播/发现过程，或改为怀疑而非确认"));
    }

    public List<Issue> validateChange(KnowledgeChange change) {
        if (change.certainty() != KnowledgeCertainty.CONFIRMED) return List.of();
        if (change.sourceEventId() != null || containsAny(change.evidence(), "告诉", "得知", "发现", "目睹", "收到")) {
            return List.of();
        }
        return List.of(new Issue(
                "KNOWLEDGE_BOUNDARY",
                ReviewSeverity.BLOCKER,
                "确认知识没有传播或发现证据",
                change.evidence(),
                null,
                "关联来源事件或提供明确传播证据"));
    }

    private boolean containsAny(String value, String... tokens) {
        if (value == null) return false;
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }
}
