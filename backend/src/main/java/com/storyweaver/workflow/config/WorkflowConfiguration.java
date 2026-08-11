package com.storyweaver.workflow.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkflowProperties.class)
public class WorkflowConfiguration {

    @Bean(destroyMethod = "close")
    ScheduledExecutorService workflowRecoveryExecutor() {
        return Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("workflow-recovery-", 0).factory());
    }
}
