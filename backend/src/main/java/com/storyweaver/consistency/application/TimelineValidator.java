package com.storyweaver.consistency.application;

import com.storyweaver.consistency.application.ConsistencyModels.Issue;
import com.storyweaver.consistency.application.ConsistencyModels.TimelineEvent;
import com.storyweaver.consistency.domain.ReviewSeverity;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TimelineValidator {
    public List<Issue> validate(String latestStoryTime, TimelineEvent proposed) {
        LocalDate latest = date(latestStoryTime);
        LocalDate next = date(proposed.storyTime());
        if (latest == null || next == null || !next.isBefore(latest)) return List.of();
        return List.of(new Issue(
                "TIMELINE",
                ReviewSeverity.BLOCKER,
                "新事件日期早于已提交时间线",
                proposed.evidence(),
                "latest=" + latestStoryTime,
                "修正故事时间或明确使用倒叙标记"));
    }

    public LocalDate date(String value) {
        if (value == null || value.length() < 10) return null;
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
