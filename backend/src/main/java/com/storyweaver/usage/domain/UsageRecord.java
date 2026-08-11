package com.storyweaver.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usage_record")
public class UsageRecord {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 24)
    private String agent;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(name = "request_id", length = 160)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private UsageStatus status;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "reasoning_tokens", nullable = false)
    private int reasoningTokens;

    @Column(name = "prompt_cache_hit_tokens", nullable = false)
    private int promptCacheHitTokens;

    @Column(name = "prompt_cache_miss_tokens", nullable = false)
    private int promptCacheMissTokens;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "duration_ms", nullable = false)
    private long durationMillis;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "pricing_rule_id")
    private UUID pricingRuleId;

    @Column(name = "pricing_rule_version", length = 80)
    private String pricingRuleVersion;

    @Column(name = "estimated_cost", precision = 18, scale = 8)
    private BigDecimal estimatedCost;

    @Column(name = "actual_cost", precision = 18, scale = 8)
    private BigDecimal actualCost;

    @Column(length = 3)
    private String currency;

    protected UsageRecord() {}

    public UsageRecord(
            UUID projectId,
            UUID userId,
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
            String currency) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.userId = userId;
        this.agent = agent;
        this.model = model;
        this.requestId = requestId;
        this.status = status;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.reasoningTokens = reasoningTokens;
        this.promptCacheHitTokens = promptCacheHitTokens;
        this.promptCacheMissTokens = promptCacheMissTokens;
        this.attempts = attempts;
        this.durationMillis = durationMillis;
        this.requestedAt = requestedAt;
        this.pricingRuleId = pricingRuleId;
        this.pricingRuleVersion = pricingRuleVersion;
        this.estimatedCost = estimatedCost;
        this.actualCost = actualCost;
        this.currency = currency;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getAgent() {
        return agent;
    }

    public String getModel() {
        return model;
    }

    public String getRequestId() {
        return requestId;
    }

    public UsageStatus getStatus() {
        return status;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public int getReasoningTokens() {
        return reasoningTokens;
    }

    public int getPromptCacheHitTokens() {
        return promptCacheHitTokens;
    }

    public int getPromptCacheMissTokens() {
        return promptCacheMissTokens;
    }

    public int getAttempts() {
        return attempts;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public UUID getPricingRuleId() {
        return pricingRuleId;
    }

    public String getPricingRuleVersion() {
        return pricingRuleVersion;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public BigDecimal getActualCost() {
        return actualCost;
    }

    public String getCurrency() {
        return currency;
    }
}
