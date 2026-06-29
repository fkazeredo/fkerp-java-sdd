package com.fksoft.domain.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.internal.Payout;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Payout} aggregate (SPEC-0017): the BRL conversion by {@code
 * settlementRate} (BR1, DL-0049), the status machine (BR2), the refund-needs-origin rule (BR7) and
 * the installment completion (the payout is EXECUTED only when all installments are EXECUTED, BR6).
 */
class PayoutAggregateTest {

  private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");

  @Test
  void supplierSettlementInUsdConvertsToBrlByTheSettlementRate() {
    // Acceptance: USD 500 × 5,70 = R$ 2.850,00 settled (BR1, DL-0049).
    Payout payout =
        Payout.open(
            command(
                PayoutKind.SUPPLIER_SETTLEMENT,
                new Payee("sup-12", PayeeType.SUPPLIER),
                Money.of(new BigDecimal("500.00"), "USD"),
                new BigDecimal("5.70"),
                null),
            NOW,
            "dev");

    assertThat(payout.settlementRate()).isEqualByComparingTo("5.700000");
    assertThat(payout.settledBrl()).isEqualTo(Money.of(new BigDecimal("2850.00"), "BRL"));
    assertThat(payout.status()).isEqualTo(PayoutStatus.PENDING);
  }

  @Test
  void aRefundWithoutOriginIsRejected() {
    assertThatThrownBy(
            () ->
                Payout.open(
                    command(
                        PayoutKind.REFUND,
                        new Payee("cust-1", PayeeType.CUSTOMER),
                        Money.of(new BigDecimal("100.00"), "BRL"),
                        null,
                        null),
                    NOW,
                    "dev"))
        .isInstanceOf(PayoutRefundOriginRequiredException.class);
  }

  @Test
  void aRefundWithOriginIsAccepted() {
    Payout payout =
        Payout.open(
            new CreatePayoutCommand(
                PayoutKind.REFUND,
                new Payee("cust-1", PayeeType.CUSTOMER),
                "b-1",
                "cancellation-charge-9",
                Money.of(new BigDecimal("100.00"), "BRL"),
                null,
                null,
                null,
                null),
            NOW,
            "dev");

    assertThat(payout.originRef()).isEqualTo("cancellation-charge-9");
    assertThat(payout.kind()).isEqualTo(PayoutKind.REFUND);
  }

  @Test
  void aPayoutIsExecutedOnlyWhenAllInstallmentsAreExecuted() {
    Payout payout =
        Payout.open(
            new CreatePayoutCommand(
                PayoutKind.AGENT_COMMISSION,
                new Payee("ag-1", PayeeType.AGENT),
                null,
                null,
                Money.of(new BigDecimal("90.00"), "BRL"),
                null,
                3,
                null,
                null),
            NOW,
            "dev");
    assertThat(payout.toView().installments()).hasSize(3);

    // Execute installment 1 of 3 → payout still EXECUTING (not all done).
    payout.beginNextExecution(NOW, "dev");
    payout.confirmInstallment(1, null, NOW, "dev");
    assertThat(payout.status()).isEqualTo(PayoutStatus.EXECUTING);

    // Execute 2 and 3 → now all EXECUTED → payout EXECUTED (BR6).
    payout.beginNextExecution(NOW, "dev");
    payout.confirmInstallment(2, null, NOW, "dev");
    payout.beginNextExecution(NOW, "dev");
    payout.confirmInstallment(3, null, NOW, "dev");
    assertThat(payout.status()).isEqualTo(PayoutStatus.EXECUTED);
    assertThat(payout.allInstallmentsExecuted()).isTrue();
  }

  @Test
  void aFailedInstallmentLeavesThePayoutFailedNotExecuted() {
    Payout payout =
        Payout.open(
            command(
                PayoutKind.AGENT_COMMISSION,
                new Payee("ag-1", PayeeType.AGENT),
                Money.of(new BigDecimal("50.00"), "BRL"),
                null,
                null),
            NOW,
            "dev");

    payout.beginNextExecution(NOW, "dev");
    payout.failInstallment(1, NOW, "dev");

    assertThat(payout.status()).isEqualTo(PayoutStatus.FAILED); // explicit failure, no false paid
    assertThat(payout.allInstallmentsExecuted()).isFalse();
  }

  @Test
  void confirmingAnAlreadyExecutedInstallmentIsAnIdempotentNoOp() {
    Payout payout =
        Payout.open(
            command(
                PayoutKind.AGENT_COMMISSION,
                new Payee("ag-1", PayeeType.AGENT),
                Money.of(new BigDecimal("50.00"), "BRL"),
                null,
                null),
            NOW,
            "dev");
    payout.beginNextExecution(NOW, "dev");
    payout.confirmInstallment(1, null, NOW, "dev");

    // Re-confirming the same installment is a no-op (empty), the payout stays EXECUTED.
    assertThat(payout.confirmInstallment(1, null, NOW, "dev")).isEmpty();
    assertThat(payout.status()).isEqualTo(PayoutStatus.EXECUTED);
  }

  @Test
  void aZeroOrNegativeAmountIsRejected() {
    assertThatThrownBy(
            () ->
                Payout.open(
                    command(
                        PayoutKind.AGENT_COMMISSION,
                        new Payee("ag-1", PayeeType.AGENT),
                        Money.of(new BigDecimal("0.00"), "BRL"),
                        null,
                        null),
                    NOW,
                    "dev"))
        .isInstanceOf(PayoutAmountInvalidException.class);
  }

  @Test
  void aNonPositiveSettlementRateIsRejected() {
    assertThatThrownBy(
            () ->
                Payout.open(
                    command(
                        PayoutKind.SUPPLIER_SETTLEMENT,
                        new Payee("sup-1", PayeeType.SUPPLIER),
                        Money.of(new BigDecimal("500.00"), "USD"),
                        new BigDecimal("0.000000"),
                        null),
                    NOW,
                    "dev"))
        .isInstanceOf(PayoutAmountInvalidException.class);
  }

  private static CreatePayoutCommand command(
      PayoutKind kind,
      Payee payee,
      Money amount,
      BigDecimal settlementRate,
      List<Money> installmentAmounts) {
    return new CreatePayoutCommand(
        kind, payee, null, null, amount, settlementRate, null, null, installmentAmounts);
  }
}
