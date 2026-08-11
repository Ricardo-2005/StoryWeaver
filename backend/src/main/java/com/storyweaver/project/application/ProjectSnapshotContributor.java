package com.storyweaver.project.application;

import java.util.UUID;

public interface ProjectSnapshotContributor {

    String sectionName();

    Object contribute(UUID projectId);
}
