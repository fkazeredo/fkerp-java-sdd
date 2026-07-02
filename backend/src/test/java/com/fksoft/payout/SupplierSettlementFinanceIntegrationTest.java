package com.fksoft.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.CreatePayoutCommand;
import com.fksoft.domain.payout.Payee;
import com.fksoft.domain.payout.PayeeTypeCodes;
import com.fksoft.domain.payout.PaymentOutcome;
import com.fksoft.domain.payout.PayoutKindCodes;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutStatus;
import com.fksoft.domain.payout.PayoutView;
import com.fksoft.infra.integration.payment.MockPayoutJobDispatcher;
import com.fksoft.infra.integration.payment.PayoutExecutionService;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the supplier-settlement → Finance wiring and the receipt archival
 * (SPEC-0017 BR4/BR5; DL-0049/DL-0051): liquidating a supplier at 5,70 baixa R$ 2.850 to Finance
 * EXACTLY once (re-delivered event does not double-post), archives a PAYMENT_PROOF in the
 * Compliance vault, and fires the {@code SupplierSettled} event once the payout executes. This is
 * the supervisor's hard requirement: "the supplier settlement posts to Finance exactly once".
 */
class SupplierSettlementFinanceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PayoutService payoutService;
  @Autowired private PayoutExecutionService executionService;
  @Autowired private MockPayoutJobDispatcher dispatcher;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM document_attachments");
    jdbcTemplate.execute("DELETE FROM documents");
    jdbcTemplate.execute("DELETE FROM posted_event_entries");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
    jdbcTemplate.execute("DELETE FROM processed_payout_webhooks");
    jdbcTemplate.execute("DELETE FROM mock_payout_jobs");
    jdbcTemplate.execute("DELETE FROM payout_installments");
    jdbcTemplate.execute("DELETE FROM payouts");
  }

  @Test
  void liquidatingASupplierAt570PostsR2850ToFinanceOnceAndArchivesThePaymentProof() {
    // Acceptance: liquidar a 5,70 baixa R$ 2.850, arquiva o comprovante, posta ao Finance uma vez.
    PayoutView created =
        payoutService.create(
            new CreatePayoutCommand(
                PayoutKindCodes.SUPPLIER_SETTLEMENT,
                new Payee("sup-12", PayeeTypeCodes.SUPPLIER),
                "b71",
                null,
                Money.of(new BigDecimal("500.00"), "USD"),
                new BigDecimal("5.70"),
                null,
                null,
                null),
            "dev");

    executionService.execute(created.id(), PaymentOutcome.SUCCEEDED);
    dispatcher.deliverDue(); // async webhook confirms → SupplierSettled published

    assertThat(payoutService.getById(created.id()).status()).isEqualTo(PayoutStatus.EXECUTED);

    // Finance posted the supplier settlement as ONE PAYABLE SUPPLIER_SETTLEMENT of R$ 2.850 (BR5).
    List<Map<String, Object>> ap =
        jdbcTemplate.queryForList(
            "SELECT direction, entry_type, amount, currency FROM ledger_entries "
                + "WHERE entry_type = 'SUPPLIER_SETTLEMENT'");
    assertThat(ap).hasSize(1);
    assertThat(ap.get(0).get("direction")).isEqualTo("PAYABLE");
    assertThat((BigDecimal) ap.get(0).get("amount")).isEqualByComparingTo("2850.00");
    assertThat(ap.get(0).get("currency")).isEqualTo("BRL");

    // The receipt was archived in the vault as a PAYMENT_PROOF.
    Integer proofs =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM documents WHERE type = 'PAYMENT_PROOF'", Integer.class);
    assertThat(proofs).isEqualTo(1);
  }

  @Test
  void aReDeliveredSupplierSettledEventDoesNotDoublePostToFinance() {
    // BR3/DL-0051: the Finance posting is idempotent per (payoutId, SUPPLIER_SETTLEMENT).
    PayoutView created =
        payoutService.create(
            new CreatePayoutCommand(
                PayoutKindCodes.SUPPLIER_SETTLEMENT,
                new Payee("sup-12", PayeeTypeCodes.SUPPLIER),
                "b71",
                null,
                Money.of(new BigDecimal("500.00"), "USD"),
                new BigDecimal("5.70"),
                null,
                null,
                null),
            "dev");
    executionService.execute(created.id(), PaymentOutcome.SUCCEEDED);
    dispatcher.deliverDue();

    // Force the same webhook to be re-delivered (the receiver dedups it; even if it did not, the
    // Finance posting is idempotent per (payoutId, SUPPLIER_SETTLEMENT)).
    jdbcTemplate.execute("UPDATE mock_payout_jobs SET delivered = false");
    dispatcher.deliverDue();

    Integer apEntries =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE entry_type = 'SUPPLIER_SETTLEMENT'",
            Integer.class);
    assertThat(apEntries).isEqualTo(1); // posted exactly once
    Integer proofs =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM documents WHERE type = 'PAYMENT_PROOF'", Integer.class);
    assertThat(proofs).isEqualTo(1); // archived exactly once
  }
}
