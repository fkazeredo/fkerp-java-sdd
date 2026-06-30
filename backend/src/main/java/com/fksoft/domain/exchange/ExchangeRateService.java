package com.fksoft.domain.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the exchange module (SPEC-0003): pins sell rates (append-only) and serves
 * the prevailing rate, both as REST and as the {@link ExchangeRateProvider} Open-Host port. The
 * audit actor is resolved by delivery and passed in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService implements ExchangeRateProvider {

  private static final int RATE_SCALE = 6;

  private final PinnedSellRateRepository repository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Pins a new sell rate for a pair (BR1/BR2). The rate must be strictly positive (BR4); {@code
   * effectiveFrom} defaults to now and may be past, present or future (BR5).
   *
   * @param pair the currency pair
   * @param rate the sell rate (must be &gt; 0)
   * @param effectiveFrom when the rate starts to prevail, or {@code null} for now
   * @param note optional note
   * @param actor who pins it (audit)
   * @return the pinned rate view
   * @throws ExchangeRateInvalidException when the rate is not strictly positive (BR4)
   */
  @Transactional
  public PinnedSellRateView pin(
      CurrencyPair pair, BigDecimal rate, Instant effectiveFrom, String note, String actor) {
    if (rate == null || rate.signum() <= 0) {
      throw new ExchangeRateInvalidException();
    }
    BigDecimal scaledRate = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    Instant when = effectiveFrom != null ? effectiveFrom : clock.instant();
    PinnedSellRate pinned =
        repository.save(PinnedSellRate.pin(pair, scaledRate, when, actor, note, clock.instant()));
    events.publishEvent(new RatePinned(pair, scaledRate, when, actor, pinned.createdAt()));
    log.info(
        "RatePinned pair={} rate={} effectiveFrom={} setBy={}",
        pair.asText(),
        scaledRate,
        when,
        actor);
    return pinned.toView();
  }

  /**
   * The prevailing rate for a pair now, as a REST read.
   *
   * @throws ExchangeRateNotFoundException when no rate is in effect yet (BR3)
   */
  @Transactional(readOnly = true)
  public PinnedSellRateView current(CurrencyPair pair) {
    return currentRate(pair).orElseThrow(ExchangeRateNotFoundException::new);
  }

  /** Paginated pinning history for a pair, newest first. */
  @Transactional(readOnly = true)
  public Page<PinnedSellRateView> history(CurrencyPair pair, Pageable pageable) {
    return repository
        .findByCurrencyPairOrderByEffectiveFromDesc(pair.asText(), pageable)
        .map(PinnedSellRate::toView);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<PinnedSellRateView> currentRate(CurrencyPair pair) {
    return repository
        .findFirstByCurrencyPairAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            pair.asText(), clock.instant())
        .map(PinnedSellRate::toView);
  }
}
