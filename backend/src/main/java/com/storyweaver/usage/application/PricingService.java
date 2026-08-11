package com.storyweaver.usage.application;

import com.storyweaver.usage.domain.PricingRule;
import com.storyweaver.usage.repository.PricingRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricingService {
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private final PricingRuleRepository rules;

    public PricingService(PricingRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public PriceResult price(String model, TokenUsage usage, Instant at) {
        return rules.findApplicable(model, at)
                .map(rule -> calculate(rule, usage))
                .orElseGet(() -> PriceResult.unpriced(model));
    }

    @Transactional(readOnly = true)
    public List<PricingRule> listRules() {
        return rules.findAllByOrderByModelAscEffectiveFromDesc();
    }

    private PriceResult calculate(PricingRule rule, TokenUsage usage) {
        int standardInput = Math.max(0, usage.promptTokens() - usage.cacheHitTokens() - usage.cacheMissTokens());
        BigDecimal amount = tokens(standardInput, rule.getInputPerMillion())
                .add(tokens(usage.completionTokens(), rule.getOutputPerMillion()))
                .add(tokens(usage.reasoningTokens(), rule.getReasoningPerMillion()))
                .add(tokens(usage.cacheHitTokens(), rule.getCacheHitPerMillion()))
                .add(tokens(usage.cacheMissTokens(), rule.getCacheMissPerMillion()))
                .setScale(8, RoundingMode.HALF_UP);
        return new PriceResult(true, rule.getId(), rule.getRuleVersion(), rule.getModel(), rule.getCurrency(), amount);
    }

    private BigDecimal tokens(int count, BigDecimal perMillion) {
        return BigDecimal.valueOf(count).multiply(perMillion).divide(MILLION, 12, RoundingMode.HALF_UP);
    }

    public record TokenUsage(
            int promptTokens, int completionTokens, int reasoningTokens, int cacheHitTokens, int cacheMissTokens) {}

    public record PriceResult(
            boolean priced,
            java.util.UUID ruleId,
            String ruleVersion,
            String model,
            String currency,
            BigDecimal amount) {
        static PriceResult unpriced(String model) {
            return new PriceResult(false, null, null, model, null, null);
        }
    }
}
