package com.storyweaver.workflow.application;

import java.util.UUID;

public record WorkflowApprovedEvent(UUID runId, UUID projectId, UUID chapterId, UUID userId) {}
