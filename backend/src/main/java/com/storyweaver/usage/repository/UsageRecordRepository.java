package com.storyweaver.usage.repository;

import com.storyweaver.usage.domain.UsageRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {
    List<UsageRecord> findAllByProjectIdOrderByRequestedAtDesc(UUID projectId);

    @Query("select coalesce(sum(r.estimatedCost), 0) from UsageRecord r where r.projectId = :projectId")
    BigDecimal sumProjectCost(@Param("projectId") UUID projectId);

    @Query(
            """
            select coalesce(sum(r.estimatedCost), 0) from UsageRecord r
            where r.userId = :userId and r.requestedAt >= :from
            """)
    BigDecimal sumUserCostSince(@Param("userId") UUID userId, @Param("from") Instant from);
}
