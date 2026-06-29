package com.fksoft.domain.exchange;

import java.util.Optional;

/**
 * Open-Host port of the exchange module: serves the prevailing pinned sell rate for a pair to
 * in-process consumers (e.g. Quoting, SPEC-0005), without exposing this module's entities or
 * repository (Spring Modulith). "Prevailing" means the rate with the greatest {@code effectiveFrom}
 * that is {@code <=} now (BR3); a future-dated rate is not served before its time.
 */
public interface ExchangeRateProvider {

  /**
   * The rate currently in effect for the pair, if any.
   *
   * @param pair the currency pair
   * @return the prevailing rate, or empty if none is in effect yet
   */
  Optional<PinnedSellRateView> currentRate(CurrencyPair pair);
}
