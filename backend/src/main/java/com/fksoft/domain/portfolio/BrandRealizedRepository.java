package com.fksoft.domain.portfolio;

import com.fksoft.domain.ModuleInternal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command/query repository for the {@link BrandRealized} projection (SPEC-0020 BR4; DL-0062).
 * Module-internal. The realized total for a (brand, period, metric) is aggregated from the rows the
 * service filters by period (computed from {@code occurredAt} in UTC).
 */
@ModuleInternal
public interface BrandRealizedRepository extends JpaRepository<BrandRealized, UUID> {

  /** Whether a contribution from this event already exists (idempotency, BR4). */
  boolean existsByMetricAndSourceRef(String metric, String sourceRef);

  /** All contributions for a brand and metric (the service filters by period and aggregates). */
  List<BrandRealized> findByBrandRefAndMetric(String brandRef, String metric);
}
