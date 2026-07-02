package com.fksoft.domain.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the market rate (SPEC-0011, slice 1): records observations (append-only)
 * and serves the prevailing market rate as the {@link MarketRateProvider} port. The v1 path is
 * manual contingency registration (DL-0025); a real feed is a future adapter implementing the same
 * port. Rates are normalized to scale 6 and must be strictly positive (BR1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketRateService implements MarketRateProvider {

  private static final int RATE_SCALE = 6;

  private final MarketRateRepository repository;
  private final Clock clock;

  /**
   * Records a market-rate observation (BR1). The rate must be strictly positive; {@code observedAt}
   * defaults to now and may be past or present (a future observation is allowed but is not served
   * as "market now" before its time).
   *
   * @param pair the currency pair
   * @param rate the market rate (must be &gt; 0)
   * @param observedAt when the market showed this rate, or {@code null} for now
   * @param source where the observation came from (market-rate source cadastro code; MANUAL for
   *     contingency)
   * @param actor who records it (audit)
   * @return the recorded observation view
   * @throws ExchangeRateInvalidException when the rate is not strictly positive (BR1)
   */
  @Transactional
  public MarketRateView record(
      CurrencyPair pair, BigDecimal rate, Instant observedAt, String source, String actor) {
    if (rate == null || rate.signum() <= 0) {
      throw new ExchangeRateInvalidException();
    }
    BigDecimal scaledRate = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    Instant when = observedAt != null ? observedAt : clock.instant();
    MarketRate observation =
        repository.save(MarketRate.record(pair, scaledRate, when, source, actor, clock.instant()));
    log.info(
        "MarketRateRecorded pair={} rate={} observedAt={} source={} recordedBy={}",
        pair.asText(),
        scaledRate,
        when,
        source,
        actor);
    return observation.toView();
  }

  /**
   * The market rate prevailing for a pair now, as a REST read.
   *
   * @throws ExchangeMarketRateNotFoundException when no observation exists yet (BR1)
   */
  @Transactional(readOnly = true)
  public MarketRateView currentMarket(CurrencyPair pair) {
    return marketRateAt(pair, clock.instant())
        .orElseThrow(ExchangeMarketRateNotFoundException::new);
  }

  /** Paginated observation history for a pair, newest first. */
  @Transactional(readOnly = true)
  public Page<MarketRateView> history(CurrencyPair pair, Pageable pageable) {
    return repository
        .findByCurrencyPairOrderByObservedAtDesc(pair.asText(), pageable)
        .map(MarketRate::toView);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<MarketRateView> marketRateAt(CurrencyPair pair, Instant at) {
    return repository
        .findFirstByCurrencyPairAndObservedAtLessThanEqualOrderByObservedAtDesc(pair.asText(), at)
        .map(MarketRate::toView);
  }
}
