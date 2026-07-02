package com.fksoft.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.finance.AccountingPeriodId;
import com.fksoft.domain.finance.EntryTypeCodes;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.Party;
import com.fksoft.domain.finance.PartyTypeCodes;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Merchant-trap regression for Payout (SPEC-0017 BR7; DL-0024/DL-0051): executing a customer REFUND
 * records the refund baixa and a REFUND_PROOF receipt, but does <strong>NOT</strong> cancel or net
 * the supplier obligation (the ALL_SALES_FINAL supplier PAYABLE from the cancellation). The two are
 * distinct facts that never compensate — this proves Payout preserves the merchant trap. A REFUND
 * without origin is also rejected (BR7).
 */
class RefundMerchantTrapIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PayoutService payoutService;
  @Autowired private PayoutExecutionService executionService;
  @Autowired private MockPayoutJobDispatcher dispatcher;
  @Autowired private FinanceService financeService;
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
  void executingACustomerRefundDoesNotCancelTheSupplierObligation() {
    // Given a pre-existing supplier PAYABLE (the ALL_SALES_FINAL cancellation charge already in
    // Finance — the merchant obligation that does NOT vanish when the customer is refunded).
    financeService.register(
        LedgerDirection.PAYABLE,
        new Party("sup-merchant", PartyTypeCodes.SUPPLIER),
        Money.of(new BigDecimal("800.00"), "BRL"),
        EntryTypeCodes.SUPPLIER_SETTLEMENT,
        AccountingPeriodId.of("2026-06"),
        "dev");

    // When a customer refund is executed (referencing its origin obligation, BR7).
    PayoutView refund =
        payoutService.create(
            new CreatePayoutCommand(
                PayoutKindCodes.REFUND,
                new Payee("cust-1", PayeeTypeCodes.CUSTOMER),
                "b71",
                "cancellation-charge-merchant",
                Money.of(new BigDecimal("300.00"), "BRL"),
                null,
                null,
                null,
                null),
            "dev");
    executionService.execute(refund.id(), PaymentOutcome.SUCCEEDED);
    dispatcher.deliverDue();

    assertThat(payoutService.getById(refund.id()).status()).isEqualTo(PayoutStatus.EXECUTED);

    // Then the supplier PAYABLE is STILL there, untouched (the merchant trap holds, DL-0024).
    Integer supplierPayables =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_entries "
                + "WHERE entry_type = 'SUPPLIER_SETTLEMENT' AND direction = 'PAYABLE'",
            Integer.class);
    assertThat(supplierPayables).isEqualTo(1);

    // And the refund posted its OWN separate REFUND PAYABLE baixa + a REFUND_PROOF receipt.
    Integer refundPayables =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE entry_type = 'REFUND' AND direction = 'PAYABLE'",
            Integer.class);
    assertThat(refundPayables).isEqualTo(1);
    Integer refundProofs =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM documents WHERE type = 'REFUND_PROOF'", Integer.class);
    assertThat(refundProofs).isEqualTo(1);
  }

  @Test
  void aRefundWithoutOriginIsRejected() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                payoutService.create(
                    new CreatePayoutCommand(
                        PayoutKindCodes.REFUND,
                        new Payee("cust-1", PayeeTypeCodes.CUSTOMER),
                        null,
                        null, // no origin
                        Money.of(new BigDecimal("100.00"), "BRL"),
                        null,
                        null,
                        null,
                        null),
                    "dev"))
        .isInstanceOf(com.fksoft.domain.payout.PayoutRefundOriginRequiredException.class);
  }
}
