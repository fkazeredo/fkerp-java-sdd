package com.fksoft.domain.exchange;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-model service for the book's FX exposure and reports (SPEC-0011, slice 10c): the aggregate
 * {@code LiveExposure} of the open positions with a drift alert (BR6/BR9/DL-0027) and the {@code
 * PromoFxResult} for a period (subsidy × drift × gap). These are projections over the positions
 * (persistence.md): they never mutate the aggregate. The drift alert is evaluated on the aggregate
 * and published as {@code BookPositionDrifted} when it crosses — it never blocks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeExposureService {

  private static final String BOOK_CURRENCY = "BRL";
  private static final int MONEY_SCALE = 2;

  /** Drift alert threshold: 2% of the open foreign exposure valued at freeze (DL-0027). */
  private static final BigDecimal DRIFT_ALERT_PCT = new BigDecimal("0.02");

  private final FxPositionRepository repository;
  private final ForwardContractRepository forwardRepository;
  private final MarketRateProvider marketRateProvider;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * The book's live exposure: the aggregate of OPEN positions (subsidy + current drift, BR6) and
   * the drift alert (BR9). Fase 19h (SPEC-0032/DL-0130 — revisa a DL-0027): OPEN forward contracts
   * count as <strong>coverage</strong>, so the alert threshold is computed over the
   * <strong>unhedged</strong> exposure only — a fully hedged book never alerts on drift (the
   * covered leg is locked). Publishes {@code BookPositionDrifted} when the alert is on. Projection
   * only.
   *
   * @return the live exposure read-model
   */
  @Transactional(readOnly = true)
  public LiveExposureView liveExposure() {
    Instant asOf = clock.instant();
    List<FxPosition> open = repository.findByStatus(FxPositionStatus.OPEN);

    BigDecimal subsidy = BigDecimal.ZERO;
    BigDecimal drift = BigDecimal.ZERO;
    // Per-currency foreign exposure and its BRL-at-freeze value, to apportion the hedge coverage.
    java.util.Map<String, BigDecimal> foreignByCurrency = new java.util.HashMap<>();
    java.util.Map<String, BigDecimal> baseBrlByCurrency = new java.util.HashMap<>();
    for (FxPosition position : open) {
      subsidy = subsidy.add(position.subsidyBrl());
      drift = drift.add(position.driftAt(currentMarketRate(position, asOf)));
      foreignByCurrency.merge(position.currency(), position.foreignAmount(), BigDecimal::add);
      baseBrlByCurrency.merge(
          position.currency(), position.exposureValueAtFreeze(), BigDecimal::add);
    }
    subsidy = subsidy.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    drift = drift.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    // Coverage (SPEC-0032): OPEN forward notionals per currency reduce the unhedged share; the
    // BRL-at-freeze base of each currency is scaled by its uncovered fraction.
    List<ForwardContract> forwards =
        forwardRepository.findByStatusOrderByMaturityDateAsc(ForwardStatus.OPEN);
    BigDecimal exposureBase = BigDecimal.ZERO;
    BigDecimal unhedgedBase = BigDecimal.ZERO;
    for (var entry : baseBrlByCurrency.entrySet()) {
      String currency = entry.getKey();
      BigDecimal baseBrl = entry.getValue();
      BigDecimal foreign = foreignByCurrency.getOrDefault(currency, BigDecimal.ZERO);
      BigDecimal hedged =
          forwards.stream()
              .filter(forward -> forward.currency().equals(currency))
              .map(ForwardContract::notional)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      exposureBase = exposureBase.add(baseBrl);
      if (foreign.signum() <= 0) {
        continue;
      }
      BigDecimal uncoveredForeign = foreign.subtract(hedged).max(BigDecimal.ZERO);
      BigDecimal uncoveredFraction = uncoveredForeign.divide(foreign, 8, RoundingMode.HALF_UP);
      unhedgedBase = unhedgedBase.add(baseBrl.multiply(uncoveredFraction));
    }
    unhedgedBase = unhedgedBase.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal threshold =
        unhedgedBase.multiply(DRIFT_ALERT_PCT).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    boolean alert = drift.abs().compareTo(threshold) > 0 && unhedgedBase.signum() > 0;
    BigDecimal total = subsidy.add(drift).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    if (alert) {
      events.publishEvent(
          new BookPositionDrifted(
              asOf, Money.of(drift, BOOK_CURRENCY), Money.of(threshold, BOOK_CURRENCY), asOf));
      log.info("BookPositionDrifted asOf={} drift={} threshold={}", asOf, drift, threshold);
    }
    return new LiveExposureView(
        asOf,
        open.size(),
        Money.of(subsidy, BOOK_CURRENCY),
        Money.of(drift, BOOK_CURRENCY),
        Money.of(total, BOOK_CURRENCY),
        Money.of(threshold, BOOK_CURRENCY),
        alert,
        forwards.size(),
        Money.of(unhedgedBase, BOOK_CURRENCY));
  }

  /**
   * The FX promo result for a period: the gap of the positions opened in {@code YYYY-MM}, split
   * into subsidy and drift (realized when closed, marked-to-market when open). Projection only.
   *
   * @param period the period in {@code YYYY-MM}
   * @return the promo-fx read-model
   * @throws ExchangePeriodInvalidException when the period is malformed (400)
   */
  @Transactional(readOnly = true)
  public PromoFxResultView promoFxResult(String period) {
    YearMonth month = parsePeriod(period);
    Instant from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant to = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant asOf = clock.instant();
    List<FxPosition> positions = repository.findOpenedBetween(from, to);

    BigDecimal subsidy = BigDecimal.ZERO;
    BigDecimal drift = BigDecimal.ZERO;
    for (FxPosition position : positions) {
      subsidy = subsidy.add(position.subsidyBrl());
      drift =
          drift.add(
              position.isOpen()
                  ? position.driftAt(currentMarketRate(position, asOf))
                  : position.realizedDriftBrl());
    }
    subsidy = subsidy.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    drift = drift.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal totalGap = subsidy.add(drift).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    return new PromoFxResultView(
        month.toString(),
        positions.size(),
        Money.of(subsidy, BOOK_CURRENCY),
        Money.of(drift, BOOK_CURRENCY),
        Money.of(totalGap, BOOK_CURRENCY));
  }

  private BigDecimal currentMarketRate(FxPosition position, Instant at) {
    CurrencyPair pair = new CurrencyPair(position.currency(), BOOK_CURRENCY);
    return marketRateProvider
        .marketRateAt(pair, at)
        .map(MarketRateView::rate)
        .orElse(position.marketAtFreeze());
  }

  private YearMonth parsePeriod(String period) {
    try {
      return YearMonth.parse(period);
    } catch (DateTimeParseException | NullPointerException invalid) {
      throw new ExchangePeriodInvalidException();
    }
  }
}
