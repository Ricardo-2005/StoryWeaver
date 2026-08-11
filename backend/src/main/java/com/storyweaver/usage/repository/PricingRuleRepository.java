package com.storyweaver.usage.repository;

import com.storyweaver.usage.domain.PricingRule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {
    @Query(
            """
            select rule from PricingRule rule
            where rule.model = :model and rule.active = true
              and rule.effectiveFrom <= :at
              and (rule.effectiveTo is null or rule.effectiveTo > :at)
            order by rule.effectiveFrom desc
            limit 1
            """)
    Optional<PricingRule> findApplicable(@Param("model") String model, @Param("at") Instant at);

    List<PricingRule> findAllByOrderByModelAscEffectiveFromDesc();
}
