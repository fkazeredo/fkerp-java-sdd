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
  private final MarketRateProvider marketRateProvider;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * The book's live exposure: the aggregate of OPEN positions (subsidy + current drift, BR6) and
   * the drift alert (BR9). Publishes {@code BookPositionDrifted} when the alert is on. Projection
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
    BigDecimal exposureBase = BigDecimal.ZERO;
    for (FxPosition position : open) {
      subsidy = subsidy.add(position.subsidyBrl());
      drift = drift.add(position.driftAt(currentMarketRate(position, asOf)));
      exposureBase = exposureBase.add(position.exposureValueAtFreeze());
    }
    subsidy = subsidy.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    drift = drift.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    BigDecimal threshold =
        exposureBase.multiply(DRIFT_ALERT_PCT).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    boolean alert = drift.abs().compareTo(threshold) > 0;
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
        alert);
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
