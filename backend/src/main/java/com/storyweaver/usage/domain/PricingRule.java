package com.storyweaver.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pricing_rule")
public class PricingRule {
    @Id
    private UUID id;

    @Column(name = "rule_version", nullable = false, length = 80)
    private String ruleVersion;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "input_per_million", nullable = false, precision = 18, scale = 8)
    private BigDecimal inputPerMillion;

    @Column(name = "output_per_million", nullable = false, precision = 18, scale = 8)
    private BigDecimal outputPerMillion;

    @Column(name = "reasoning_per_million", nullable = false, precision = 18, scale = 8)
    private BigDecimal reasoningPerMillion;

    @Column(name = "cache_hit_per_million", nullable = false, precision = 18, scale = 8)
    private BigDecimal cacheHitPerMillion;

    @Column(name = "cache_miss_per_million", nullable = false, precision = 18, scale = 8)
    private BigDecimal cacheMissPerMillion;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PricingRule() {}

    public UUID getId() {
        return id;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public String getModel() {
        return model;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getInputPerMillion() {
        return inputPerMillion;
    }

    public BigDecimal getOutputPerMillion() {
        return outputPerMillion;
    }

    public BigDecimal getReasoningPerMillion() {
        return reasoningPerMillion;
    }

    public BigDecimal getCacheHitPerMillion() {
        return cacheHitPerMillion;
    }

    public BigDecimal getCacheMissPerMillion() {
        return cacheMissPerMillion;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }
}
