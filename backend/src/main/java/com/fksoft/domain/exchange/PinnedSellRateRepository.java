package com.fksoft.domain.exchange;

import com.fksoft.domain.ModuleInternal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Append-only repository for {@link PinnedSellRate}. Module-internal. The prevailing-rate query
 * returns the row with the greatest {@code effectiveFrom} not in the future (BR3); the history
 * query returns newest first.
 */
@ModuleInternal
public interface PinnedSellRateRepository extends JpaRepository<PinnedSellRate, UUID> {

  /** The prevailing rate for a pair at {@code now}: greatest {@code effectiveFrom <= now} (BR3). */
  Optional<PinnedSellRate>
      findFirstByCurrencyPairAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
          String currencyPair, Instant now);

  /** Paginated history for a pair, newest first. */
  Page<PinnedSellRate> findByCurrencyPairOrderByEffectiveFromDesc(
      String currencyPair, Pageable pageable);
}
