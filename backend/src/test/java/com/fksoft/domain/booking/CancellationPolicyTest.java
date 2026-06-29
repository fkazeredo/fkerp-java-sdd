package com.fksoft.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link CancellationPolicy} value object (SPEC-0010 BR2/BR3/BR4/BR8/BR9):
 * window selection and penalty math across several {@code hoursBefore}, the ALL_SALES_FINAL
 * behavior, and the merchant-of-record cost-bearer resolution. Pure domain — no Spring, no DB.
 */
class CancellationPolicyTest {

  private static final Money PAID = Money.of(new BigDecimal("480.00"), "BRL");

  @Test
  void picksTheTightestApplicableWindowForThePenalty() {
    // 50% within 24h, 25% within 72h (BR2): the smallest hoursBefore >= hoursUntilService wins.
    CancellationPolicy policy =
        new CancellationPolicy(
            CancellationType.STANDARD,
            List.of(
                new PenaltyWindow(24, new BigDecimal("0.50")),
                new PenaltyWindow(72, new BigDecimal("0.25"))),
            true,
            CostBearer.AGENCY,
            false);

    // 10h until service -> within the 24h window -> 50%.
    assertThat(policy.penaltyFor(10, PAID)).isEqualTo(Money.of(new BigDecimal("240.00"), "BRL"));
    // 48h until service -> outside 24h, inside 72h -> 25%.
    assertThat(policy.penaltyFor(48, PAID)).isEqualTo(Money.of(new BigDecimal("120.00"), "BRL"));
    // exactly 24h -> still within the 24h window (>= is inclusive) -> 50%.
    assertThat(policy.penaltyFor(24, PAID)).isEqualTo(Money.of(new BigDecimal("240.00"), "BRL"));
  }

  @Test
  void chargesNoPenaltyWhenNoWindowApplies() {
    CancellationPolicy policy =
        CancellationPolicy.standardWindow(24, new BigDecimal("0.50"), CostBearer.AGENCY);

    // 100h until service -> no window covers it -> penalty 0 (BR2).
    assertThat(policy.penaltyFor(100, PAID)).isEqualTo(Money.zero("BRL"));
  }

  @Test
  void standardWithoutWindowsAndCustomWithoutWindowsChargeZero() {
    assertThat(CancellationPolicy.standardNoWindows().penaltyFor(1, PAID))
        .isEqualTo(Money.zero("BRL"));

    CancellationPolicy customNoWindows =
        new CancellationPolicy(CancellationType.CUSTOM, List.of(), true, CostBearer.AGENCY, false);
    // BR4: CUSTOM with no windows behaves as STANDARD with penalty 0.
    assertThat(customNoWindows.penaltyFor(1, PAID)).isEqualTo(Money.zero("BRL"));
  }

  @Test
  void roundsThePenaltyHalfUpToScaleTwo() {
    // 33.33% of 100.00 = 33.333 -> 33.33 (HALF_UP, scale 2).
    CancellationPolicy policy =
        CancellationPolicy.standardWindow(48, new BigDecimal("0.3333"), CostBearer.AGENCY);

    Money penalty = policy.penaltyFor(10, Money.of(new BigDecimal("100.00"), "BRL"));

    assertThat(penalty).isEqualTo(Money.of(new BigDecimal("33.33"), "BRL"));
  }

  @Test
  void allSalesFinalChargesNoWindowPenaltyButResolvesCostBearerByMerchantFlag() {
    CancellationPolicy affiliate =
        new CancellationPolicy(
            CancellationType.ALL_SALES_FINAL,
            List.of(new PenaltyWindow(24, new BigDecimal("0.50"))),
            false,
            CostBearer.SUPPLIER,
            false);
    // ALL_SALES_FINAL never computes a window penalty (the supplier cost is handled separately).
    assertThat(affiliate.penaltyFor(1, PAID)).isEqualTo(Money.zero("BRL"));
    assertThat(affiliate.allSalesFinalCostBearer()).isEqualTo(CostBearer.SUPPLIER);

    CancellationPolicy merchant =
        new CancellationPolicy(
            CancellationType.ALL_SALES_FINAL, List.of(), false, CostBearer.SUPPLIER, true);
    // Merchant of record (Portal de Experiências case): Acme assumes it (BR8/DL-0021).
    assertThat(merchant.allSalesFinalCostBearer()).isEqualTo(CostBearer.ACME);
  }

  @Test
  void rejectsMalformedWindows() {
    assertThatThrownBy(() -> new PenaltyWindow(-1, new BigDecimal("0.50")))
        .isInstanceOf(CancellationPolicyInvalidException.class);
    assertThatThrownBy(() -> new PenaltyWindow(24, new BigDecimal("1.50")))
        .isInstanceOf(CancellationPolicyInvalidException.class);
    assertThatThrownBy(() -> new PenaltyWindow(24, new BigDecimal("-0.10")))
        .isInstanceOf(CancellationPolicyInvalidException.class);
  }
}
