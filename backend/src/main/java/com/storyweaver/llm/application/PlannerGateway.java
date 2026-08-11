package com.storyweaver.llm.application;

import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.application.AgentContracts.ChapterPlan;
import java.util.UUID;

public interface PlannerGateway {
    ChapterPlan plan(UUID projectId, UUID userId, AgentInput request);
}
