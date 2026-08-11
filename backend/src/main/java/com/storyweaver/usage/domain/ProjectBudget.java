package com.storyweaver.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_budget")
public class ProjectBudget {
    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "task_token_limit", nullable = false)
    private int taskTokenLimit;

    @Column(name = "user_daily_cost_limit", nullable = false, precision = 18, scale = 8)
    private BigDecimal userDailyCostLimit;

    @Column(name = "project_cost_limit", nullable = false, precision = 18, scale = 8)
    private BigDecimal projectCostLimit;

    @Column(name = "writer_output_token_limit", nullable = false)
    private int writerOutputTokenLimit;

    @Column(name = "planner_reasoning_token_limit", nullable = false)
    private int plannerReasoningTokenLimit;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectBudget() {}

    public ProjectBudget(
            UUID projectId,
            int taskTokenLimit,
            BigDecimal userDailyCostLimit,
            BigDecimal projectCostLimit,
            int writerOutputTokenLimit,
            int plannerReasoningTokenLimit,
            Instant now) {
        this.projectId = projectId;
        this.createdAt = now;
        update(
                taskTokenLimit,
                userDailyCostLimit,
                projectCostLimit,
                writerOutputTokenLimit,
                plannerReasoningTokenLimit,
                now);
    }

    public void update(
            int taskTokenLimit,
            BigDecimal userDailyCostLimit,
            BigDecimal projectCostLimit,
            int writerOutputTokenLimit,
            int plannerReasoningTokenLimit,
            Instant now) {
        this.taskTokenLimit = taskTokenLimit;
        this.userDailyCostLimit = userDailyCostLimit;
        this.projectCostLimit = projectCostLimit;
        this.writerOutputTokenLimit = writerOutputTokenLimit;
        this.plannerReasoningTokenLimit = plannerReasoningTokenLimit;
        this.updatedAt = now;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getTaskTokenLimit() {
        return taskTokenLimit;
    }

    public BigDecimal getUserDailyCostLimit() {
        return userDailyCostLimit;
    }

    public BigDecimal getProjectCostLimit() {
        return projectCostLimit;
    }

    public int getWriterOutputTokenLimit() {
        return writerOutputTokenLimit;
    }

    public int getPlannerReasoningTokenLimit() {
        return plannerReasoningTokenLimit;
    }

    public long getVersion() {
        return version;
    }
}
