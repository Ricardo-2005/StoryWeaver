package com.storyweaver.workflow.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storyweaver.workflow")
public record WorkflowProperties(
        Duration staleRunTimeout,
        Duration heartbeatInterval,
        Duration recoveryInterval,
        Duration contextTtl,
        Duration eventStreamTimeout,
        Duration eventPollInterval,
        int maxActiveRunsPerProject) {}
