package com.storyweaver.workflow.domain;

import java.util.EnumSet;
import java.util.Set;

public enum WorkflowStatus {
    CREATED,
    PREFLIGHT,
    CONTEXT_READY,
    PLANNING,
    PLAN_READY,
    WRITING,
    TEXT_READY,
    EXTRACTING,
    VALIDATING,
    REVIEWING,
    WAITING_APPROVAL,
    REVISION_REQUIRED,
    COMMITTING,
    COMPLETED,
    BLOCKED,
    FAILED,
    CANCELLED,
    ROLLED_BACK;

    private static final Set<WorkflowStatus> EXECUTING = EnumSet.of(
            CREATED,
            PREFLIGHT,
            CONTEXT_READY,
            PLANNING,
            PLAN_READY,
            WRITING,
            TEXT_READY,
            EXTRACTING,
            VALIDATING,
            REVIEWING);

    private static final Set<WorkflowStatus> STREAM_COMPLETE =
            EnumSet.of(WAITING_APPROVAL, REVISION_REQUIRED, COMPLETED, BLOCKED, FAILED, CANCELLED, ROLLED_BACK);

    public boolean isExecuting() {
        return EXECUTING.contains(this);
    }

    public boolean streamComplete() {
        return STREAM_COMPLETE.contains(this);
    }
}
