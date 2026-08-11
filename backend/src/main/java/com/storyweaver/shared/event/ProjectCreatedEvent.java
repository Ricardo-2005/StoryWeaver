package com.storyweaver.shared.event;

import java.util.UUID;

/** Shared event keeps optional project extensions out of the project module dependency graph. */
public record ProjectCreatedEvent(UUID projectId, UUID ownerId, UUID baseSkillVersionId) {}
