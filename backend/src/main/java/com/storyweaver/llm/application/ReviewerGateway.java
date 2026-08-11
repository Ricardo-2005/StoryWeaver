package com.storyweaver.llm.application;

import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.application.AgentContracts.ReviewResult;
import java.util.UUID;

public interface ReviewerGateway {
    ReviewResult review(UUID projectId, UUID userId, AgentInput request);
}
