package com.fksoft.domain.exchange;

import com.fksoft.domain.exchange.internal.FxPosition;
import com.fksoft.domain.exchange.internal.FxPositionRepository;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for FX positions (SPEC-0011, slice 10b): opens a position from a confirmed
 * sale, accruing the subsidy (BR2/BR3), and closes it from the recorded supplier settlement (BR5),
 * computing the realized drift and total gap.
 *
 * <p>The provenance (foreign cost, frozen rate) is passed in by the caller (Reconciliation, which
 * already holds the frozen quote snapshot — DL-0028), so this module never depends on {@code
 * quoting}/{@code booking} (which would form a dependency cycle with {@code exchange}). The
 * subsidy/drift/gap math lives here; the caller reuses its already-recorded settlement rate, so the
 * per-case computation is not duplicated. Opening is idempotent per booking (BR2). The position
 * only opens when the cost is in a foreign currency and a market rate is known at the freeze
 * instant — otherwise it logs and skips (it never invents a market-at-freeze).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxPositionService {

  private static final String BOOK_CURRENCY = "BRL";

  private final FxPositionRepository repository;
  private final MarketRateProvider marketRateProvider;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Opens an FX position for a confirmed sale, capturing the market rate prevailing at the freeze
   * instant and accruing the subsidy (BR2/BR3). Idempotent: an existing position for the booking is
   * left untouched. Skips (no position) when the cost is in BRL (no FX exposure) or no market rate
   * is known.
   *
   * @param bookingId the confirmed booking id
   * @param foreignCost the supplier cost in the foreign currency (the FX exposure leg)
   * @param pinnedRate the frozen sell rate used (scale 6, &gt; 0)
   * @param occurredAt the freeze instant (confirmation) for the market rate
   */
  @Transactional
  public void openPosition(
      UUID bookingId, Money foreignCost, BigDecimal pinnedRate, Instant occurredAt) {
    if (repository.existsByBookingId(bookingId)) {
      return;
    }
    if (BOOK_CURRENCY.equals(foreignCost.currency())) {
      log.info(
          "No FX exposure for booking {}: supplier cost is in {} (book currency)",
          bookingId,
          BOOK_CURRENCY);
      return;
    }
    CurrencyPair pair = new CurrencyPair(foreignCost.currency(), BOOK_CURRENCY);
    Optional<MarketRateView> market = marketRateProvider.marketRateAt(pair, occurredAt);
    if (market.isEmpty()) {
      log.warn(
          "Cannot open FX position for booking {}: no market rate for {} at {}",
          bookingId,
          pair.asText(),
          occurredAt);
      return;
    }
    BigDecimal marketAtFreeze = market.get().rate();
    FxPosition position =
        FxPosition.open(
            bookingId,
            foreignCost.amount(),
            foreignCost.currency(),
            pinnedRate,
            marketAtFreeze,
            occurredAt);
    try {
      repository.saveAndFlush(position);
    } catch (DataIntegrityViolationException alreadyOpen) {
      log.info("FX position already open for booking {}", bookingId);
      return;
    }
    Money subsidy = Money.of(position.subsidyBrl(), BOOK_CURRENCY);
    events.publishEvent(
        new RateSubsidyAccrued(
            bookingId, subsidy, marketAtFreeze, pinnedRate, position.openedAt()));
    log.info(
        "RateSubsidyAccrued bookingId={} subsidy={} marketAtFreeze={} pinnedRate={}",
        bookingId,
        subsidy.amount(),
        marketAtFreeze,
        pinnedRate);
  }

  /**
   * Closes the FX position for a booking from the recorded supplier settlement rate (BR5),
   * computing the realized drift and total gap, and publishes {@code FxPositionClosed}. No-op when
   * there is no open position for the booking (e.g. a BRL-cost sale) or it is already closed.
   *
   * @param bookingId the booking whose settlement was recorded
   * @param settlementRate the supplier settlement rate (scale 6, &gt; 0)
   * @param occurredAt the settlement instant
   */
  @Transactional
  public void closePosition(UUID bookingId, BigDecimal settlementRate, Instant occurredAt) {
    Optional<FxPosition> existing = repository.findByBookingId(bookingId);
    if (existing.isEmpty()) {
      return;
    }
    FxPosition position = existing.get();
    if (!position.isOpen()) {
      return;
    }
    position.close(settlementRate, occurredAt);
    repository.save(position);
    events.publishEvent(
        new FxPositionClosed(
            bookingId,
            Money.of(position.subsidyBrl(), BOOK_CURRENCY),
            Money.of(position.realizedDriftBrl(), BOOK_CURRENCY),
            Money.of(position.totalGapBrl(), BOOK_CURRENCY),
            occurredAt));
    log.info(
        "FxPositionClosed bookingId={} subsidy={} realizedDrift={} totalGap={}",
        bookingId,
        position.subsidyBrl(),
        position.realizedDriftBrl(),
        position.totalGapBrl());
  }

  /**
   * The FX position for a booking and its decomposition, marking an open position to the current
   * market.
   *
   * @param bookingId the booking id
   * @return the position view
   * @throws ExchangePositionNotFoundException when no position exists for the booking
   */
  @Transactional(readOnly = true)
  public FxPositionView getByBooking(UUID bookingId) {
    FxPosition position =
        repository.findByBookingId(bookingId).orElseThrow(ExchangePositionNotFoundException::new);
    BigDecimal marketNow = currentMarketRate(position);
    return position.toView(marketNow);
  }

  private BigDecimal currentMarketRate(FxPosition position) {
    CurrencyPair pair = new CurrencyPair(position.currency(), BOOK_CURRENCY);
    return marketRateProvider
        .marketRateAt(pair, clock.instant())
        .map(MarketRateView::rate)
        .orElse(position.marketAtFreeze());
  }
}
