package com.fksoft.domain.exchange;

import java.time.Instant;
import java.util.Optional;

/**
 * Port for the market rate of a currency pair (SPEC-0011 BR1, DL-0025). "Market now" is the most
 * recent observation with {@code observedAt <=} the requested instant. The v1 source is manual
 * contingency registration; a real external feed is a future adapter (ACL) that implements this
 * same port, so the domain never depends on a concrete provider.
 */
public interface MarketRateProvider {

  /**
   * The market rate prevailing for the pair at the given instant, if any observation exists up to
   * that instant.
   *
   * @param pair the currency pair
   * @param at the instant to evaluate ("now" for the current market)
   * @return the prevailing market-rate observation, or empty if none is known up to {@code at}
   */
  Optional<MarketRateView> marketRateAt(CurrencyPair pair, Instant at);
}
