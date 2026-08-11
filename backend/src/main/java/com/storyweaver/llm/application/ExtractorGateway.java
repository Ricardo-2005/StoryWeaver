package com.storyweaver.llm.application;

import com.storyweaver.llm.application.AgentContracts.AgentInput;
import com.storyweaver.llm.application.AgentContracts.ExtractionResult;
import java.util.UUID;

public interface ExtractorGateway {
    ExtractionResult extract(UUID projectId, UUID userId, AgentInput request);
}
