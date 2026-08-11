package com.storyweaver.consistency.application;

import com.storyweaver.consistency.application.ConsistencyModels.Issue;
import com.storyweaver.consistency.domain.ReviewSeverity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CanonReferenceValidator {
    public List<Issue> requireEvidence(String category, String evidence) {
        if (evidence != null && !evidence.isBlank()) return List.of();
        return List.of(new Issue(category, ReviewSeverity.BLOCKER, "候选状态缺少正文证据", "", null, "提供正文证据片段或段落键"));
    }
}
