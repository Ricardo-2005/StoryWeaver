package com.storyweaver.skill.domain;

public enum SkillScope {
    BASE(0),
    PROJECT(1),
    CHAPTER(2);

    private final int priority;

    SkillScope(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
