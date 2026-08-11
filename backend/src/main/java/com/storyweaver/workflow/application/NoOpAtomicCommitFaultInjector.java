package com.storyweaver.workflow.application;

import com.storyweaver.workflow.domain.WorkflowRun;
import org.springframework.stereotype.Component;

@Component
public class NoOpAtomicCommitFaultInjector implements AtomicCommitFaultInjector {
    @Override
    public void beforeFinalize(WorkflowRun run) {
        // Extension point for deterministic transaction rollback tests.
    }
}
