package com.fksoft.domain.exchange;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FX position aggregate (SPEC-0011): opened when a confirmed sale carries a foreign-currency cost
 * priced at the frozen rate (BR2), it decomposes the sell gap into <em>subsidy</em> (intentional,
 * at opening — BR3) and <em>drift</em> (market risk, marked to market while open — BR4; realized on
 * settlement — BR5). The canonical example (OVERVIEW 7.2): USD 1000, pinned 5.40, market-at-freeze
 * 5.55, settled 5.70 → subsidy 150, realizedDrift 150, totalGap 300.
 *
 * <p>The economic invariant proven by tests: {@code totalGap == subsidy + realizedDrift ==
 * (settlementRate − pinnedRate) × foreignAmount}. Money is BRL at scale 2 HALF_UP; rates are scale
 * 6. Module-internal: other modules read positions through the exposure read-models/views.
 */
@Entity
@Table(name = "fx_positions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class FxPosition {

  private static final int MONEY_SCALE = 2;
  static final String BOOK_CURRENCY = "BRL";

  @Id private UUID id;

  private UUID bookingId;

  private BigDecimal foreignAmount;
  private String currency;

  private BigDecimal pinnedRate;
  private BigDecimal marketAtFreeze;

  private BigDecimal subsidyBrl;

  private BigDecimal settlementRate;
  private BigDecimal realizedDriftBrl;
  private BigDecimal totalGapBrl;

  @Enumerated(EnumType.STRING)
  private FxPositionStatus status;

  private Instant openedAt;
  private Instant updatedAt;

  @Version private Long version;

  /**
   * Opens a position and accrues the subsidy (BR2/BR3): {@code subsidy = (marketAtFreeze −
   * pinnedRate) × foreignAmount}, scale 2 HALF_UP. The caller guarantees the rates are positive,
   * scale 6.
   *
   * @param bookingId the confirmed booking
   * @param foreignAmount the supplier cost in the foreign currency (the exposure leg)
   * @param currency the foreign currency code
   * @param pinnedRate the frozen sell rate (scale 6)
   * @param marketAtFreeze the market rate prevailing at the freeze instant (scale 6)
   * @param now the opening instant (UTC)
   * @return a new, persistable OPEN position with the subsidy accrued
   */
  public static FxPosition open(
      UUID bookingId,
      BigDecimal foreignAmount,
      String currency,
      BigDecimal pinnedRate,
      BigDecimal marketAtFreeze,
      Instant now) {
    FxPosition position = new FxPosition();
    position.id = UUID.randomUUID();
    position.bookingId = bookingId;
    position.foreignAmount = foreignAmount;
    position.currency = currency;
    position.pinnedRate = pinnedRate;
    position.marketAtFreeze = marketAtFreeze;
    position.subsidyBrl =
        marketAtFreeze
            .subtract(pinnedRate)
            .multiply(foreignAmount)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    position.status = FxPositionStatus.OPEN;
    position.openedAt = now;
    position.updatedAt = now;
    return position;
  }

  /**
   * The mark-to-market drift while OPEN (BR4): {@code (marketNow − marketAtFreeze) ×
   * foreignAmount}, scale 2 HALF_UP. Read-only derivation; does not mutate the position.
   *
   * @param marketNow the current market rate (scale 6)
   * @return the drift in BRL
   */
  public BigDecimal driftAt(BigDecimal marketNow) {
    return marketNow
        .subtract(marketAtFreeze)
        .multiply(foreignAmount)
        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * The value of the foreign exposure at the freeze market rate (book-position alert base, BR9).
   */
  public BigDecimal exposureValueAtFreeze() {
    return marketAtFreeze.multiply(foreignAmount).abs().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * Closes the position on settlement (BR5): {@code realizedDrift = (settlementRate −
   * marketAtFreeze) × foreignAmount} and {@code totalGap = subsidy + realizedDrift}. By
   * construction {@code totalGap == (settlementRate − pinnedRate) × foreignAmount}. Idempotent:
   * closing an already-closed position is a no-op.
   *
   * @param settlementRate the supplier settlement rate (scale 6, &gt; 0)
   * @param now the closing instant (UTC)
   */
  public void close(BigDecimal settlementRate, Instant now) {
    if (status == FxPositionStatus.CLOSED) {
      return;
    }
    this.settlementRate = settlementRate;
    this.realizedDriftBrl =
        settlementRate
            .subtract(marketAtFreeze)
            .multiply(foreignAmount)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    this.totalGapBrl = subsidyBrl.add(realizedDriftBrl).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    this.status = FxPositionStatus.CLOSED;
    this.updatedAt = now;
  }

  /** Whether the position is still open. */
  public boolean isOpen() {
    return status == FxPositionStatus.OPEN;
  }

  /**
   * Projects the position to its public view, computing the mark-to-market drift against {@code
   * marketNow} when OPEN (null when CLOSED, where the realized drift is the truth).
   *
   * @param marketNow the current market rate to mark an open position to, or {@code null}
   * @return the view
   */
  public FxPositionView toView(BigDecimal marketNow) {
    Money drift =
        status == FxPositionStatus.OPEN && marketNow != null
            ? Money.of(driftAt(marketNow), BOOK_CURRENCY)
            : null;
    return new FxPositionView(
        bookingId,
        Money.of(foreignAmount, currency),
        pinnedRate,
        marketAtFreeze,
        Money.of(subsidyBrl, BOOK_CURRENCY),
        drift,
        settlementRate,
        realizedDriftBrl == null ? null : Money.of(realizedDriftBrl, BOOK_CURRENCY),
        totalGapBrl == null ? null : Money.of(totalGapBrl, BOOK_CURRENCY),
        status,
        openedAt);
  }
}
