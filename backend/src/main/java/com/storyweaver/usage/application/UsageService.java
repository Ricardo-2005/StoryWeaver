package com.storyweaver.usage.application;

import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.usage.domain.UsageRecord;
import com.storyweaver.usage.domain.UsageStatus;
import com.storyweaver.usage.repository.UsageRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsageService {
    private final UsageRecordRepository records;
    private final ProjectAccessService projectAccess;
    private final PricingService pricing;
    private final MeterRegistry meters;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final UsageAttributionContext attribution;

    public UsageService(
            UsageRecordRepository records,
            ProjectAccessService projectAccess,
            PricingService pricing,
            MeterRegistry meters,
            Clock clock,
            JdbcTemplate jdbc,
            UsageAttributionContext attribution) {
        this.records = records;
        this.projectAccess = projectAccess;
        this.pricing = pricing;
        this.meters = meters;
        this.clock = clock;
        this.jdbc = jdbc;
        this.attribution = attribution;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UsageRecord record(UsageInput input) {
        var requestedAt = clock.instant();
        var price = pricing.price(
                input.model(),
                new PricingService.TokenUsage(
                        input.promptTokens(),
                        input.completionTokens(),
                        input.reasoningTokens(),
                        input.cacheHitTokens(),
                        input.cacheMissTokens()),
                requestedAt);
        UsageRecord record = records.saveAndFlush(new UsageRecord(
                input.projectId(),
                input.userId(),
                input.agent(),
                input.model(),
                input.requestId(),
                input.status(),
                input.promptTokens(),
                input.completionTokens(),
                input.reasoningTokens(),
                input.cacheHitTokens(),
                input.cacheMissTokens(),
                input.attempts(),
                input.durationMillis(),
                requestedAt,
                price.ruleId(),
                price.ruleVersion(),
                price.amount(),
                input.status() == UsageStatus.SUCCEEDED ? price.amount() : null,
                price.currency()));
        UUID reconstructionJobId = attribution.currentReconstructionJob();
        if (reconstructionJobId != null) {
            jdbc.update(
                    "UPDATE usage_record SET reconstruction_job_id=? WHERE id=?", reconstructionJobId, record.getId());
        }
        meters.counter(
                        "storyweaver.llm.requests",
                        "agent",
                        input.agent(),
                        "model",
                        input.model(),
                        "status",
                        input.status().name())
                .increment();
        meters.counter("storyweaver.llm.input.tokens", "agent", input.agent(), "model", input.model())
                .increment(input.promptTokens());
        meters.counter("storyweaver.llm.output.tokens", "agent", input.agent(), "model", input.model())
                .increment(input.completionTokens());
        meters.counter("storyweaver.llm.cache.hit.tokens", "agent", input.agent(), "model", input.model())
                .increment(input.cacheHitTokens());
        meters.timer(
                        "storyweaver.llm.latency",
                        "agent",
                        input.agent(),
                        "model",
                        input.model(),
                        "status",
                        input.status().name())
                .record(Duration.ofMillis(input.durationMillis()));
        if (price.priced()) {
            meters.counter(
                            "storyweaver.llm.cost",
                            "agent",
                            input.agent(),
                            "model",
                            input.model(),
                            "status",
                            input.status().name())
                    .increment(price.amount().doubleValue());
        }
        return record;
    }

    @Transactional(readOnly = true)
    public List<UsageRecord> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return records.findAllByProjectIdOrderByRequestedAtDesc(projectId);
    }

    @Transactional(readOnly = true)
    public CostSummary summary(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        List<UsageRecord> values = records.findAllByProjectIdOrderByRequestedAtDesc(projectId);
        BigDecimal estimated = values.stream()
                .map(UsageRecord::getEstimatedCost)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = values.stream()
                .map(UsageRecord::getActualCost)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long unpriced = values.stream()
                .filter(value -> value.getPricingRuleId() == null)
                .count();
        return new CostSummary(projectId, estimated, actual, unpriced, values.size());
    }

    public record CostSummary(
            UUID projectId, BigDecimal estimatedCost, BigDecimal actualCost, long unpricedRequests, long requests) {}

    public record UsageInput(
            UUID projectId,
            UUID userId,
            String agent,
            String model,
            String requestId,
            UsageStatus status,
            int promptTokens,
            int completionTokens,
            int reasoningTokens,
            int cacheHitTokens,
            int cacheMissTokens,
            int attempts,
            long durationMillis) {}
}
