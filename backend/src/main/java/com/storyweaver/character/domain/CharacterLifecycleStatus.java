package com.storyweaver.character.domain;

public enum CharacterLifecycleStatus {
    CANDIDATE,
    ACTIVE,
    INACTIVE,
    DECEASED,
    MISSING,
    LEFT_STORY,
    MERGED,
    REJECTED,
    ARCHIVED,
    PURGED;

    public boolean currentContextEligible() {
        return this == ACTIVE || this == INACTIVE || this == MISSING;
    }

    public boolean historicalRetrievalEligible() {
        return this != MERGED && this != REJECTED && this != ARCHIVED && this != PURGED;
    }
}
