package com.fksoft.domain.exchange;

import com.fksoft.domain.ModuleInternal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Append-only repository for {@link MarketRate}. Module-internal. The market-now query returns the
 * row with the greatest {@code observedAt <= at} (BR1); the history query returns newest first.
 */
@ModuleInternal
public interface MarketRateRepository extends JpaRepository<MarketRate, UUID> {

  /** The market rate for a pair at {@code at}: greatest {@code observedAt <= at} (BR1). */
  Optional<MarketRate> findFirstByCurrencyPairAndObservedAtLessThanEqualOrderByObservedAtDesc(
      String currencyPair, Instant at);

  /** Paginated history for a pair, newest first. */
  Page<MarketRate> findByCurrencyPairOrderByObservedAtDesc(String currencyPair, Pageable pageable);
}
