package com.storyweaver.workflow.application;

import com.storyweaver.workflow.domain.WorkflowRun;

public interface AtomicCommitFaultInjector {
    void beforeFinalize(WorkflowRun run);
}
