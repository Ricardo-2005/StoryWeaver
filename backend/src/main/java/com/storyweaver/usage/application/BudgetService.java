package com.storyweaver.usage.application;

import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.usage.config.BudgetProperties;
import com.storyweaver.usage.domain.ProjectBudget;
import com.storyweaver.usage.repository.ProjectBudgetRepository;
import com.storyweaver.usage.repository.UsageRecordRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {
    private final ProjectBudgetRepository budgets;
    private final UsageRecordRepository usage;
    private final ProjectAccessService projectAccess;
    private final BudgetProperties defaults;
    private final Clock clock;

    public BudgetService(
            ProjectBudgetRepository budgets,
            UsageRecordRepository usage,
            ProjectAccessService projectAccess,
            BudgetProperties defaults,
            Clock clock) {
        this.budgets = budgets;
        this.usage = usage;
        this.projectAccess = projectAccess;
        this.defaults = defaults;
        this.clock = clock;
    }

    @Transactional
    public ProjectBudget get(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return budgets.findById(projectId).orElseGet(() -> budgets.save(defaultBudget(projectId)));
    }

    @Transactional
    public ProjectBudget update(UUID projectId, UUID ownerId, long expectedVersion, BudgetValues values) {
        ProjectBudget budget = get(projectId, ownerId);
        if (budget.getVersion() != expectedVersion) {
            throw new ConflictException("optimistic_lock_conflict", "The project budget changed; reload it first");
        }
        budget.update(
                values.taskTokenLimit(),
                values.userDailyCostLimit(),
                values.projectCostLimit(),
                values.writerOutputTokenLimit(),
                values.plannerReasoningTokenLimit(),
                clock.instant());
        budgets.flush();
        return budget;
    }

    @Transactional
    public void checkWorkflow(
            UUID projectId, UUID userId, int projectedTokens, int writerOutputTokens, int plannerReasoningTokens) {
        ProjectBudget budget = get(projectId, userId);
        if (projectedTokens > budget.getTaskTokenLimit()) {
            blocked("task_token_budget_exceeded", "The configured per-workflow token budget is too small");
        }
        if (writerOutputTokens > budget.getWriterOutputTokenLimit()) {
            blocked("writer_output_budget_exceeded", "The writer output token limit is below the writer contract");
        }
        if (plannerReasoningTokens > budget.getPlannerReasoningTokenLimit()) {
            blocked(
                    "planner_reasoning_budget_exceeded",
                    "The planner reasoning token limit is below the planner contract");
        }
        BigDecimal projectCost = usage.sumProjectCost(projectId);
        if (projectCost.compareTo(budget.getProjectCostLimit()) >= 0) {
            blocked("project_cost_budget_exceeded", "The project cost budget has been exhausted");
        }
        var dayStart = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal dailyCost = usage.sumUserCostSince(userId, dayStart);
        if (dailyCost.compareTo(budget.getUserDailyCostLimit()) >= 0) {
            blocked("user_daily_cost_budget_exceeded", "The user daily cost budget has been exhausted");
        }
    }

    private ProjectBudget defaultBudget(UUID projectId) {
        return new ProjectBudget(
                projectId,
                defaults.defaultTaskTokenLimit(),
                defaults.defaultUserDailyCostLimit(),
                defaults.defaultProjectCostLimit(),
                defaults.defaultWriterOutputTokenLimit(),
                defaults.defaultPlannerReasoningTokenLimit(),
                clock.instant());
    }

    private void blocked(String code, String message) {
        throw new ConflictException(code, message);
    }

    public record BudgetValues(
            int taskTokenLimit,
            BigDecimal userDailyCostLimit,
            BigDecimal projectCostLimit,
            int writerOutputTokenLimit,
            int plannerReasoningTokenLimit) {}
}
