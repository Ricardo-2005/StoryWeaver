package com.storyweaver.workflow.application;

final class WorkflowCancelledException extends RuntimeException {
    WorkflowCancelledException() {
        super("Workflow was cancelled");
    }
}
