package com.fksoft.domain.reconciliation.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteSnapshot;
import com.fksoft.domain.reconciliation.CaseStatus;
import com.fksoft.domain.reconciliation.ReconciliationCurrencyMismatchException;
import com.fksoft.domain.reconciliation.SettlementInput;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the reconciliation case derivations (SPEC-0007): realized spread (BR4), FX
 * gain/loss (BR5), discrepancy flagging (BR7) and currency validation. Uses the Orlando example
 * (USD 500, pinned 5.40, settled 5.70 -> fxGainLoss -150).
 */
class ReconciliationCaseTest {

  private static final Instant NOW = Instant.parse("2026-06-26T13:00:00Z");
  private static final BigDecimal FLOOR = new BigDecimal("1.00");
  private static final BigDecimal PCT = new BigDecimal("0.005");

  private static QuoteSnapshot orlando() {
    return new QuoteSnapshot(
        UUID.randomUUID(),
        UUID.randomUUID(),
        Money.of(new BigDecimal("500.00"), "USD"),
        new BigDecimal("5.400000"),
        Money.of(new BigDecimal("2700.00"), "BRL"),
        Money.of(new BigDecimal("405.00"), "BRL"),
        Money.of(new BigDecimal("270.00"), "BRL"),
        Money.of(new BigDecimal("135.00"), "BRL"));
  }

  private static Money brl(String amount) {
    return Money.of(new BigDecimal(amount), "BRL");
  }

  @Test
  void computesRealizedSpreadAndFxGainLossAndFlagsDiscrepancy() {
    ReconciliationCase reconciliationCase =
        ReconciliationCase.open(UUID.randomUUID(), orlando(), NOW, "system");

    reconciliationCase.settle(
        new SettlementInput(
            brl("3000.00"),
            new BigDecimal("5.700000"),
            brl("2850.00"),
            brl("405.00"),
            brl("270.00")),
        FLOOR,
        PCT,
        NOW,
        "operador1");

    assertThat(reconciliationCase.realizedSpreadBrl()).isEqualByComparingTo("285.00");
    assertThat(reconciliationCase.fxGainLossBrl()).isEqualByComparingTo("-150.00");
    assertThat(reconciliationCase.discrepancyBrl()).isEqualByComparingTo("150.00");
    assertThat(reconciliationCase.status()).isEqualTo(CaseStatus.DISCREPANCY);
  }

  @Test
  void settlesWithinToleranceAsSettled() {
    ReconciliationCase reconciliationCase =
        ReconciliationCase.open(UUID.randomUUID(), orlando(), NOW, "system");

    reconciliationCase.settle(
        new SettlementInput(
            brl("3000.00"),
            new BigDecimal("5.400000"),
            brl("3000.00"),
            brl("405.00"),
            brl("270.00")),
        FLOOR,
        PCT,
        NOW,
        "operador1");

    assertThat(reconciliationCase.realizedSpreadBrl()).isEqualByComparingTo("135.00");
    assertThat(reconciliationCase.fxGainLossBrl()).isEqualByComparingTo("0.00");
    assertThat(reconciliationCase.status()).isEqualTo(CaseStatus.SETTLED);
  }

  @Test
  void keepsPartialSettlementPartiallySettled() {
    ReconciliationCase reconciliationCase =
        ReconciliationCase.open(UUID.randomUUID(), orlando(), NOW, "system");

    reconciliationCase.settle(
        new SettlementInput(brl("3000.00"), null, null, null, null), FLOOR, PCT, NOW, "operador1");

    assertThat(reconciliationCase.status()).isEqualTo(CaseStatus.PARTIALLY_SETTLED);
    assertThat(reconciliationCase.realizedSpreadBrl()).isNull();
  }

  @Test
  void rejectsCurrencyMismatch() {
    ReconciliationCase reconciliationCase =
        ReconciliationCase.open(UUID.randomUUID(), orlando(), NOW, "system");

    assertThatThrownBy(
            () ->
                reconciliationCase.settle(
                    new SettlementInput(
                        Money.of(new BigDecimal("3000.00"), "USD"), null, null, null, null),
                    FLOOR,
                    PCT,
                    NOW,
                    "operador1"))
        .isInstanceOf(ReconciliationCurrencyMismatchException.class);
  }
}
