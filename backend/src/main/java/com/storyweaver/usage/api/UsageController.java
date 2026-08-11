package com.storyweaver.usage.api;

import com.storyweaver.usage.application.BudgetService;
import com.storyweaver.usage.application.PricingService;
import com.storyweaver.usage.application.UsageService;
import com.storyweaver.usage.domain.PricingRule;
import com.storyweaver.usage.domain.ProjectBudget;
import com.storyweaver.usage.domain.UsageRecord;
import com.storyweaver.usage.domain.UsageStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UsageController {
    private final UsageService service;
    private final BudgetService budgets;
    private final PricingService pricing;

    public UsageController(UsageService service, BudgetService budgets, PricingService pricing) {
        this.service = service;
        this.budgets = budgets;
        this.pricing = pricing;
    }

    @GetMapping("/projects/{projectId}/costs")
    UsageService.CostSummary costs(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.summary(projectId, UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/projects/{projectId}/budget")
    BudgetResponse budget(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return budgetResponse(budgets.get(projectId, UUID.fromString(jwt.getSubject())));
    }

    @PutMapping("/projects/{projectId}/budget")
    BudgetResponse updateBudget(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody BudgetRequest request) {
        return budgetResponse(budgets.update(
                projectId,
                UUID.fromString(jwt.getSubject()),
                request.expectedVersion(),
                new BudgetService.BudgetValues(
                        request.taskTokenLimit(),
                        request.userDailyCostLimit(),
                        request.projectCostLimit(),
                        request.writerOutputTokenLimit(),
                        request.plannerReasoningTokenLimit())));
    }

    @GetMapping("/pricing-rules")
    List<PricingRuleResponse> pricingRules() {
        return pricing.listRules().stream().map(this::pricingResponse).toList();
    }

    @GetMapping("/projects/{projectId}/usage")
    List<UsageResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        return service.list(projectId, UUID.fromString(jwt.getSubject())).stream()
                .map(this::response)
                .toList();
    }

    private UsageResponse response(UsageRecord r) {
        return new UsageResponse(
                r.getId(),
                r.getProjectId(),
                r.getAgent(),
                r.getModel(),
                r.getRequestId(),
                r.getStatus(),
                r.getPromptTokens(),
                r.getCompletionTokens(),
                r.getReasoningTokens(),
                r.getPromptCacheHitTokens(),
                r.getPromptCacheMissTokens(),
                r.getAttempts(),
                r.getDurationMillis(),
                r.getRequestedAt(),
                r.getPricingRuleId(),
                r.getPricingRuleVersion(),
                r.getEstimatedCost(),
                r.getActualCost(),
                r.getCurrency());
    }

    private BudgetResponse budgetResponse(ProjectBudget value) {
        return new BudgetResponse(
                value.getProjectId(),
                value.getTaskTokenLimit(),
                value.getUserDailyCostLimit(),
                value.getProjectCostLimit(),
                value.getWriterOutputTokenLimit(),
                value.getPlannerReasoningTokenLimit(),
                value.getVersion());
    }

    private PricingRuleResponse pricingResponse(PricingRule value) {
        return new PricingRuleResponse(
                value.getId(),
                value.getRuleVersion(),
                value.getModel(),
                value.getCurrency(),
                value.getInputPerMillion(),
                value.getOutputPerMillion(),
                value.getReasoningPerMillion(),
                value.getCacheHitPerMillion(),
                value.getCacheMissPerMillion(),
                value.getEffectiveFrom(),
                value.getEffectiveTo());
    }

    public record UsageResponse(
            UUID id,
            UUID projectId,
            String agent,
            String model,
            String requestId,
            UsageStatus status,
            int promptTokens,
            int completionTokens,
            int reasoningTokens,
            int promptCacheHitTokens,
            int promptCacheMissTokens,
            int attempts,
            long durationMillis,
            Instant requestedAt,
            UUID pricingRuleId,
            String pricingRuleVersion,
            BigDecimal estimatedCost,
            BigDecimal actualCost,
            String currency) {}

    public record BudgetRequest(
            long expectedVersion,
            @Min(1) int taskTokenLimit,
            @NotNull @DecimalMin("0") BigDecimal userDailyCostLimit,
            @NotNull @DecimalMin("0") BigDecimal projectCostLimit,
            @Min(1) int writerOutputTokenLimit,
            @Min(1) int plannerReasoningTokenLimit) {}

    public record BudgetResponse(
            UUID projectId,
            int taskTokenLimit,
            BigDecimal userDailyCostLimit,
            BigDecimal projectCostLimit,
            int writerOutputTokenLimit,
            int plannerReasoningTokenLimit,
            long version) {}

    public record PricingRuleResponse(
            UUID id,
            String ruleVersion,
            String model,
            String currency,
            BigDecimal inputPerMillion,
            BigDecimal outputPerMillion,
            BigDecimal reasoningPerMillion,
            BigDecimal cacheHitPerMillion,
            BigDecimal cacheMissPerMillion,
            Instant effectiveFrom,
            Instant effectiveTo) {}
}
