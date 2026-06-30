package com.fksoft.domain.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the FX position decomposition (SPEC-0011 BR3-BR5). Proves the canonical OVERVIEW
 * 7.2 example with exact numbers and HALF_UP rounding (USD 1000, pinned 5.40, market-at-freeze
 * 5.55, settled 5.70 → subsidy 150, realizedDrift 150, totalGap 300), the negative-subsidy case
 * (sold above market), the mark-to-market drift while open, and the algebraic identity totalGap ==
 * (settlement − pinned) × foreignAmount.
 */
class FxPositionTest {

  private static final Instant FREEZE = Instant.parse("2026-06-26T13:00:00Z");
  private static final Instant SETTLE = Instant.parse("2026-07-26T13:00:00Z");

  private static FxPosition canonical() {
    // OVERVIEW 7.2: USD 1000, pinned 5.40, market-at-freeze 5.55.
    return FxPosition.open(
        UUID.randomUUID(),
        new BigDecimal("1000.00"),
        "USD",
        new BigDecimal("5.400000"),
        new BigDecimal("5.550000"),
        FREEZE);
  }

  @Test
  void accruesSubsidyOnOpening() {
    FxPosition position = canonical();

    // subsidy = (5.55 − 5.40) × 1000 = 150.00
    assertThat(position.subsidyBrl()).isEqualByComparingTo("150.00");
    assertThat(position.status()).isEqualTo(FxPositionStatus.OPEN);
    assertThat(position.realizedDriftBrl()).isNull();
    assertThat(position.totalGapBrl()).isNull();
  }

  @Test
  void marksDriftToMarketWhileOpen() {
    FxPosition position = canonical();

    // drift now at market 5.70 = (5.70 − 5.55) × 1000 = 150.00
    assertThat(position.driftAt(new BigDecimal("5.700000"))).isEqualByComparingTo("150.00");
    // at freeze market there is no drift yet.
    assertThat(position.driftAt(new BigDecimal("5.550000"))).isEqualByComparingTo("0.00");
    // an adverse-for-the-book move below freeze yields negative drift.
    assertThat(position.driftAt(new BigDecimal("5.450000"))).isEqualByComparingTo("-100.00");
  }

  @Test
  void closesWithRealizedDriftAndTotalGapMatchingTheCanonicalExample() {
    FxPosition position = canonical();

    position.close(new BigDecimal("5.700000"), SETTLE);

    // realizedDrift = (5.70 − 5.55) × 1000 = 150.00 ; totalGap = 150 + 150 = 300.00
    assertThat(position.realizedDriftBrl()).isEqualByComparingTo("150.00");
    assertThat(position.totalGapBrl()).isEqualByComparingTo("300.00");
    assertThat(position.status()).isEqualTo(FxPositionStatus.CLOSED);
    // identity: totalGap == (settlement − pinned) × foreignAmount = (5.70 − 5.40) × 1000 = 300.00
    assertThat(position.totalGapBrl())
        .isEqualByComparingTo(
            new BigDecimal("5.700000")
                .subtract(new BigDecimal("5.400000"))
                .multiply(new BigDecimal("1000.00")));
    // an OPEN position marks drift; a CLOSED one exposes no mark-to-market drift in its view.
    assertThat(position.toView(new BigDecimal("5.700000")).markToMarketDrift()).isNull();
  }

  @Test
  void negativeSubsidyWhenSoldAboveMarket() {
    // pinned 5.60 above market-at-freeze 5.55 → subsidy negative (sold above market).
    FxPosition position =
        FxPosition.open(
            UUID.randomUUID(),
            new BigDecimal("1000.00"),
            "USD",
            new BigDecimal("5.600000"),
            new BigDecimal("5.550000"),
            FREEZE);

    assertThat(position.subsidyBrl()).isEqualByComparingTo("-50.00");
  }

  @Test
  void roundsSubsidyHalfUpAtScaleTwo() {
    // (5.555001 − 5.550000) × 333.33 = 0.0050005 × 333.33 = 1.66681... → 1.67 HALF_UP
    FxPosition position =
        FxPosition.open(
            UUID.randomUUID(),
            new BigDecimal("333.33"),
            "USD",
            new BigDecimal("5.550000"),
            new BigDecimal("5.555001"),
            FREEZE);

    assertThat(position.subsidyBrl()).isEqualByComparingTo("1.67");
  }

  @Test
  void closeIsIdempotent() {
    FxPosition position = canonical();
    position.close(new BigDecimal("5.700000"), SETTLE);

    position.close(new BigDecimal("9.999999"), SETTLE.plusSeconds(10));

    // the second close is ignored: still the first settlement's numbers.
    assertThat(position.totalGapBrl()).isEqualByComparingTo("300.00");
    assertThat(position.settlementRate()).isEqualByComparingTo("5.700000");
  }

  @Test
  void exposureValueAtFreezeIsForeignAmountTimesMarketAtFreeze() {
    FxPosition position = canonical();

    // 1000 × 5.55 = 5550.00 (the alert base for the book position, BR9)
    assertThat(position.exposureValueAtFreeze()).isEqualByComparingTo("5550.00");
  }
}
