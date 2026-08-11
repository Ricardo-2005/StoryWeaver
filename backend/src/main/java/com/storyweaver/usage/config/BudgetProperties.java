package com.storyweaver.usage.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storyweaver.budget")
public record BudgetProperties(
        int defaultTaskTokenLimit,
        BigDecimal defaultUserDailyCostLimit,
        BigDecimal defaultProjectCostLimit,
        int defaultWriterOutputTokenLimit,
        int defaultPlannerReasoningTokenLimit) {}
