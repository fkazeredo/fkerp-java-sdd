package com.fksoft.domain.portfolio.internal;

import com.fksoft.domain.portfolio.GoalMetric;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Command/query repository for the {@link BrandGoal} aggregate (SPEC-0020). Module-internal. */
public interface BrandGoalRepository extends JpaRepository<BrandGoal, UUID> {

  /** A goal by its natural key (brand, period, metric) — unique (BR3). */
  Optional<BrandGoal> findByBrandRefAndPeriodAndMetric(
      String brandRef, String period, GoalMetric metric);

  /** Whether a goal already exists for that natural key (duplicate guard). */
  boolean existsByBrandRefAndPeriodAndMetric(String brandRef, String period, GoalMetric metric);

  /** All goals for a brand and period (across metrics). */
  List<BrandGoal> findByBrandRefAndPeriod(String brandRef, String period);
}
