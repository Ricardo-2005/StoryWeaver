package com.storyweaver.workflow.application;

import com.storyweaver.workflow.config.WorkflowProperties;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class WorkflowRecoveryWorker {
    private final WorkflowStore store;
    private final WorkflowOrchestrator orchestrator;
    private final WorkflowProperties properties;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    public WorkflowRecoveryWorker(
            WorkflowStore store,
            WorkflowOrchestrator orchestrator,
            WorkflowProperties properties,
            ScheduledExecutorService workflowRecoveryExecutor,
            Clock clock) {
        this.store = store;
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.scheduler = workflowRecoveryExecutor;
        this.clock = clock;
    }

    @PostConstruct
    void schedule() {
        scheduler.scheduleWithFixedDelay(
                this::recoverSafely,
                properties.recoveryInterval().toMillis(),
                properties.recoveryInterval().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public void recoverStaleRuns() {
        var cutoff = clock.instant().minus(properties.staleRunTimeout());
        store.staleRuns(cutoff).forEach(run -> {
            store.prepareRecovery(run.getId());
            orchestrator.submit(run.getId());
        });
    }

    private void recoverSafely() {
        try {
            recoverStaleRuns();
        } catch (RuntimeException ignored) {
            // One recovery scan must not terminate the scheduled worker.
        }
    }
}
