package com.fksoft.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.billing.BillingService;
import com.fksoft.domain.billing.CommissionInvoiceView;
import com.fksoft.domain.finance.AccountingPeriodId;
import com.fksoft.domain.finance.EntryTypeCodes;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.LedgerEntryView;
import com.fksoft.domain.finance.Party;
import com.fksoft.domain.finance.PartyTypeCodes;
import com.fksoft.domain.finance.PeriodStatus;
import com.fksoft.domain.finance.PeriodView;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.integration.nfse.BillingIssuanceService;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Golden-rule regression of SPEC-0016 (Tests Required): the issued commission NFS-e satisfies the
 * {@code DocumentRequirement} of the commission entry in Finance, so the month — which is vetoed
 * while the document is missing — can then close. This crosses Billing (issuance + archive),
 * Compliance (the document rule) and Finance (the period veto), proving BR5 end to end. It fails
 * before issuing (vetoed) and passes after (closes).
 */
class BillingSatisfiesDocumentRequirementIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private FinanceService financeService;
  @Autowired private BillingService billingService;
  @Autowired private BillingIssuanceService issuanceService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM document_attachments");
    jdbcTemplate.execute("DELETE FROM documents");
    jdbcTemplate.execute("DELETE FROM posted_event_entries");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
    jdbcTemplate.execute("DELETE FROM commission_invoices");
  }

  @Test
  void issuedNfseSatisfiesTheCommissionEntryRequirementSoTheMonthCanClose() {
    // A COMMISSION_RECEIVABLE entry of R$ 405 in 2026-07 — requires a COMMISSION_INVOICE
    // (V8/DL-0012).
    LedgerEntryView commission =
        financeService.register(
            LedgerDirection.RECEIVABLE,
            new Party("ag-1", PartyTypeCodes.AGENCY),
            Money.of(new BigDecimal("405.00"), "BRL"),
            EntryTypeCodes.COMMISSION_RECEIVABLE,
            AccountingPeriodId.of("2026-07"),
            "dev");

    // Before issuing the NF: the month is vetoed (the mandatory document is missing).
    assertThat(financeService.getPeriod(AccountingPeriodId.of("2026-07")).status())
        .isEqualTo(PeriodStatus.OPEN);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> financeService.closePeriod(AccountingPeriodId.of("2026-07"), "dev"))
        .isInstanceOf(com.fksoft.domain.finance.FinancePeriodCannotCloseException.class);

    // Issue the commission NF referencing that entry — it archives + attaches the document.
    CommissionInvoiceView draft =
        billingService.createDraft(
            commission.id(), Money.of(new BigDecimal("405.00"), "BRL"), "9999999", "1.05", "dev");
    issuanceService.issue(draft.id(), "dev");

    // After issuing: the same period closes — the NF satisfied the DocumentRequirement.
    PeriodView closed = financeService.closePeriod(AccountingPeriodId.of("2026-07"), "dev");
    assertThat(closed.status()).isEqualTo(PeriodStatus.CLOSED);
  }
}
