package com.storyweaver.workflow.application;

final class WorkflowBlockedException extends RuntimeException {
    private final String code;

    WorkflowBlockedException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() {
        return code;
    }
}
