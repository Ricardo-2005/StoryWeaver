package com.storyweaver.llm.api;

import com.storyweaver.llm.application.ModelHealthService;
import com.storyweaver.llm.application.ModelHealthService.ModelAttemptView;
import com.storyweaver.llm.application.ModelHealthService.ModelHealthView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ModelHealthController {
    private final ModelHealthService service;

    public ModelHealthController(ModelHealthService service) {
        this.service = service;
    }

    @GetMapping("/workflows/{runId}/model-attempts")
    List<ModelAttemptView> attempts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID runId) {
        return service.attempts(runId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/ai/model-health")
    ModelHealthView health() {
        return service.health();
    }
}
