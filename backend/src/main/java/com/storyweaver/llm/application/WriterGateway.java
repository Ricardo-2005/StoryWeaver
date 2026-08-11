package com.storyweaver.llm.application;

import com.storyweaver.llm.application.AgentContracts.AgentInput;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface WriterGateway {
    SseEmitter stream(UUID projectId, UUID userId, AgentInput request);
}
