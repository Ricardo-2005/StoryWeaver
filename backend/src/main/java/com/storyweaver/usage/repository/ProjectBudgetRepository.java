package com.storyweaver.usage.repository;

import com.storyweaver.usage.domain.ProjectBudget;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectBudgetRepository extends JpaRepository<ProjectBudget, UUID> {}
