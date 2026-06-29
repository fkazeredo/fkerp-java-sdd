package com.fksoft.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit/regression tests for {@link CancellationCharges} (SPEC-0010 BR3/BR5/BR11) — the merchant
 * trap, modeled and proven. The keystone test asserts that an ALL_SALES_FINAL cancellation with a
 * commercial refund produces BOTH a supplier charge AND a customer refund, that they are NOT
 * netted, and that the supplier obligation survives the refund. Pure domain — no Spring, no DB.
 */
class CancellationChargesTest {

  private static final Money SALE = Money.of(new BigDecimal("480.00"), "BRL");
  private static final Money SUPPLIER_COST = Money.of(new BigDecimal("500.00"), "USD");

  @Test
  void standardCancellationProducesASinglePenaltyWithThePolicyCostBearer() {
    CancellationPolicy policy =
        CancellationPolicy.standardWindow(24, new BigDecimal("0.50"), CostBearer.AGENCY);

    List<Charge> charges = CancellationCharges.compute(policy, 10, SALE, SUPPLIER_COST, null);

    assertThat(charges).hasSize(1);
    Charge penalty = charges.get(0);
    assertThat(penalty.kind()).isEqualTo(ChargeKind.PENALTY);
    assertThat(penalty.amount()).isEqualTo(Money.of(new BigDecimal("240.00"), "BRL"));
    assertThat(penalty.costBearer()).isEqualTo(CostBearer.AGENCY);
  }

  @Test
  void standardCancellationOutsideAnyWindowProducesNoCharge() {
    CancellationPolicy policy =
        CancellationPolicy.standardWindow(24, new BigDecimal("0.50"), CostBearer.AGENCY);

    assertThat(CancellationCharges.compute(policy, 100, SALE, SUPPLIER_COST, null)).isEmpty();
  }

  @Test
  void merchantAllSalesFinalWithRefundProducesTwoObligationsThatDoNotNetOut() {
    // Portal de Experiências case (merchant of record): ALL_SALES_FINAL, refunds the customer.
    CancellationPolicy merchant =
        new CancellationPolicy(
            CancellationType.ALL_SALES_FINAL, List.of(), false, CostBearer.SUPPLIER, true);
    Money refund = Money.of(new BigDecimal("480.00"), "BRL");

    List<Charge> charges = CancellationCharges.compute(merchant, 1, SALE, SUPPLIER_COST, refund);

    // THE TRAP: two distinct obligations, both present, neither cancelling the other.
    assertThat(charges).hasSize(2);

    Charge supplier =
        charges.stream().filter(c -> c.kind() == ChargeKind.SUPPLIER).findFirst().orElseThrow();
    Charge customerRefund =
        charges.stream()
            .filter(c -> c.kind() == ChargeKind.CUSTOMER_REFUND)
            .findFirst()
            .orElseThrow();

    // The supplier cost is due IN FULL even though the customer was refunded (not 500 - 480 = 20).
    assertThat(supplier.amount()).isEqualTo(Money.of(new BigDecimal("500.00"), "USD"));
    assertThat(customerRefund.amount()).isEqualTo(Money.of(new BigDecimal("480.00"), "BRL"));

    // Merchant of record => Acme bears both obligations (BR8/DL-0021).
    assertThat(supplier.costBearer()).isEqualTo(CostBearer.ACME);
    assertThat(customerRefund.costBearer()).isEqualTo(CostBearer.ACME);

    // And they are in different currencies — they could not even be netted (BR9 reinforces BR11).
    assertThat(supplier.amount().currency()).isNotEqualTo(customerRefund.amount().currency());
  }

  @Test
  void affiliateAllSalesFinalWithoutRefundStillChargesTheSupplierCostToTheSupplier() {
    CancellationPolicy affiliate =
        new CancellationPolicy(
            CancellationType.ALL_SALES_FINAL, List.of(), false, CostBearer.SUPPLIER, false);

    List<Charge> charges = CancellationCharges.compute(affiliate, 1, SALE, SUPPLIER_COST, null);

    assertThat(charges).hasSize(1);
    assertThat(charges.get(0).kind()).isEqualTo(ChargeKind.SUPPLIER);
    assertThat(charges.get(0).costBearer()).isEqualTo(CostBearer.SUPPLIER);
  }
}
