package com.storyweaver.consistency.application;

import com.storyweaver.consistency.application.ConsistencyModels.Issue;
import com.storyweaver.consistency.application.ConsistencyModels.ItemChange;
import com.storyweaver.consistency.domain.ItemOwnership;
import com.storyweaver.consistency.domain.ItemStatus;
import com.storyweaver.consistency.domain.ReviewSeverity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ItemOwnershipValidator {
    public List<Issue> validateDraft(ItemOwnership item, String draft) {
        if (item.getItemStatus() != ItemStatus.DESTROYED || !draft.contains(item.getItemName())) return List.of();
        if (!containsAny(draft, "使用", "启动", "挥动", "举起", "开火")) return List.of();
        return List.of(new Issue(
                "ITEM_OWNERSHIP",
                ReviewSeverity.BLOCKER,
                "已销毁道具被继续正常使用",
                item.getItemName(),
                "道具状态为 DESTROYED",
                "移除使用行为或先解释修复/替换"));
    }

    public List<Issue> validateChange(ItemOwnership current, ItemChange change) {
        List<Issue> issues = new ArrayList<>();
        if (current != null && !Objects.equals(current.getOwnerCharacterId(), change.fromOwnerCharacterId())) {
            issues.add(new Issue(
                    "ITEM_OWNERSHIP",
                    ReviewSeverity.BLOCKER,
                    "道具转移方不是当前持有者",
                    change.evidence(),
                    "当前 owner=" + current.getOwnerCharacterId(),
                    "使用当前持有者作为转移方"));
        }
        if (current != null
                && current.getItemStatus() == ItemStatus.DESTROYED
                && change.status() == ItemStatus.ACTIVE) {
            issues.add(new Issue(
                    "ITEM_OWNERSHIP",
                    ReviewSeverity.BLOCKER,
                    "已销毁道具恢复可用但没有修复流程",
                    change.evidence(),
                    "当前状态为 DESTROYED",
                    "新增有证据的修复事实后再恢复状态"));
        }
        return List.copyOf(issues);
    }

    private boolean containsAny(String value, String... tokens) {
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }
}
