package com.fksoft.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.CreatePayoutCommand;
import com.fksoft.domain.payout.Payee;
import com.fksoft.domain.payout.PayeeType;
import com.fksoft.domain.payout.PayoutKind;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutStatus;
import com.fksoft.domain.payout.PayoutView;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the Payout creation/persistence (SPEC-0017 BR1/BR6; V21): a foreign
 * supplier settlement persists the settlement rate and the BRL baixa; an installment plan persists
 * the exact cent distribution; the payout is born PENDING. Drives the {@link PayoutService} facade
 * against a real Postgres (Testcontainers), proving the schema seam.
 */
class PayoutApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PayoutService payoutService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM payout_installments");
    jdbcTemplate.execute("DELETE FROM payouts");
  }

  @Test
  void aForeignSupplierSettlementPersistsTheRateAndTheBrlBaixa() {
    // Acceptance: liquidar a 5,70 baixa R$ 2.850 (USD 500 × 5,70).
    PayoutView created =
        payoutService.create(
            new CreatePayoutCommand(
                PayoutKind.SUPPLIER_SETTLEMENT,
                new Payee("sup-12", PayeeType.SUPPLIER),
                "b71",
                null,
                Money.of(new BigDecimal("500.00"), "USD"),
                new BigDecimal("5.70"),
                null,
                null,
                null),
            "dev");

    assertThat(created.status()).isEqualTo(PayoutStatus.PENDING);
    assertThat(created.settledBrl()).isEqualTo(Money.of(new BigDecimal("2850.00"), "BRL"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT kind, currency, settlement_rate, settled_brl, status FROM payouts WHERE id = ?",
            created.id());
    assertThat(row.get("kind")).isEqualTo("SUPPLIER_SETTLEMENT");
    assertThat(row.get("currency")).isEqualTo("USD");
    assertThat((BigDecimal) row.get("settlement_rate")).isEqualByComparingTo("5.700000");
    assertThat((BigDecimal) row.get("settled_brl")).isEqualByComparingTo("2850.00");
    assertThat(row.get("status")).isEqualTo("PENDING");

    Integer installments =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM payout_installments WHERE payout_id = ?",
            Integer.class,
            created.id());
    assertThat(installments).isEqualTo(1); // à vista → one implicit installment
  }

  @Test
  void anInstallmentPlanPersistsTheExactCentDistribution() {
    PayoutView created =
        payoutService.create(
            new CreatePayoutCommand(
                PayoutKind.AGENT_COMMISSION,
                new Payee("ag-1", PayeeType.AGENT),
                null,
                null,
                Money.of(new BigDecimal("100.00"), "BRL"),
                null,
                3,
                null,
                null),
            "dev");

    List<BigDecimal> amounts =
        jdbcTemplate.queryForList(
            "SELECT amount FROM payout_installments WHERE payout_id = ? ORDER BY seq",
            BigDecimal.class,
            created.id());
    assertThat(amounts)
        .containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
    assertThat(amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("100.00");
  }

  @Test
  void listingFiltersByKindAndStatus() {
    payoutService.create(
        new CreatePayoutCommand(
            PayoutKind.AGENT_COMMISSION,
            new Payee("ag-1", PayeeType.AGENT),
            null,
            null,
            Money.of(new BigDecimal("50.00"), "BRL"),
            null,
            null,
            null,
            null),
        "dev");

    var page =
        payoutService.list(
            PayoutKind.AGENT_COMMISSION,
            PayoutStatus.PENDING,
            null,
            org.springframework.data.domain.PageRequest.of(0, 20));
    assertThat(page.getTotalElements()).isEqualTo(1);
  }
}
