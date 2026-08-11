package com.storyweaver.consistency.application;

import com.storyweaver.character.domain.CharacterState;
import com.storyweaver.character.domain.LifeStatus;
import com.storyweaver.consistency.application.ConsistencyModels.CharacterStateChange;
import com.storyweaver.consistency.application.ConsistencyModels.Issue;
import com.storyweaver.consistency.domain.ReviewSeverity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CharacterStateValidator {
    public List<Issue> validateDraft(String characterName, CharacterState state, String draft) {
        if (state.getLifeStatus() != LifeStatus.DEAD || !draft.contains(characterName)) return List.of();
        if (containsAny(draft, "回忆", "遗体", "尸体", "照片", "画像", "梦见", "提到")) return List.of();
        return List.of(new Issue(
                "CHARACTER_STATE",
                ReviewSeverity.BLOCKER,
                "死亡人物在正文中以正常行动者出现",
                characterName,
                "人物当前生命状态为 DEAD",
                "改为回忆/遗体等合理语境，或先提供明确复活事实"));
    }

    public List<Issue> validateChange(CharacterState current, CharacterStateChange change) {
        List<Issue> issues = new ArrayList<>();
        if (current.getVersion() != change.expectedVersion()) {
            issues.add(new Issue(
                    "CHARACTER_STATE",
                    ReviewSeverity.BLOCKER,
                    "人物状态版本已变化",
                    change.evidence(),
                    "expected=" + change.expectedVersion() + ", actual=" + current.getVersion(),
                    "刷新状态后重新审批"));
        }
        if (current.getLifeStatus() == LifeStatus.DEAD
                && change.lifeStatus() == LifeStatus.ALIVE
                && !containsAny(change.evidence(), "复活", "重生", "起死回生")) {
            issues.add(new Issue(
                    "CHARACTER_STATE",
                    ReviewSeverity.BLOCKER,
                    "人物从死亡恢复为存活但没有复活证据",
                    change.evidence(),
                    "当前生命状态为 DEAD",
                    "补充明确复活证据或保持 DEAD"));
        }
        return List.copyOf(issues);
    }

    private boolean containsAny(String value, String... tokens) {
        if (value == null) return false;
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }
}
