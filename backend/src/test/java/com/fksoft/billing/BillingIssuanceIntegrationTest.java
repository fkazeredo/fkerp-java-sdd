package com.fksoft.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.billing.BillingMunicipalityRejectedException;
import com.fksoft.domain.billing.BillingNfseWebserviceException;
import com.fksoft.domain.billing.BillingService;
import com.fksoft.domain.billing.CommissionInvoiceView;
import com.fksoft.domain.billing.InvoiceStatus;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.integration.nfse.BillingIssuanceService;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the NFS-e issuance flow (SPEC-0016 BR3/BR4/BR5/BR7; DL-0046/DL-0047): the
 * {@code BillingIssuanceService} orchestrator signs+transmits via the {@code NfseGateway} ACL mock,
 * archives the signed document in the Compliance vault attached to the commission entry, and the
 * Finance module posts the ISS payable by consuming the {@code CommissionInvoiceIssued} event.
 * Drives the orchestrator directly (the REST API is slice 8c-3). The traceable mock injects
 * failures via the municipality code ({@code REJECT}/{@code TIMEOUT}) to exercise BR7.
 */
class BillingIssuanceIntegrationTest extends AbstractPostgresIntegrationTest {

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
  void issuingCommissionOf405ComputesIssArchivesXmlAndPostsTheTaxToFinance() {
    // Acceptance Criteria: commission R$ 405 → ISS computed, transmitted, number/code returned, XML
    // archived in the vault, and the ISS posted to Finance.
    UUID commissionEntryId = UUID.randomUUID();
    CommissionInvoiceView draft =
        billingService.createDraft(
            commissionEntryId, Money.of(new BigDecimal("405.00"), "BRL"), "9999999", "1.05", "dev");

    CommissionInvoiceView issued = issuanceService.issue(draft.id(), "dev");

    assertThat(issued.status()).isEqualTo(InvoiceStatus.EMITIDA);
    assertThat(issued.number()).isNotBlank();
    assertThat(issued.verificationCode()).isNotBlank();
    assertThat(issued.iss()).isEqualTo(Money.of(new BigDecimal("20.25"), "BRL")); // 5% × 405
    assertThat(issued.documentId()).isNotNull();

    // The signed XML was archived in the vault as a COMMISSION_INVOICE attached to the commission.
    Map<String, Object> doc =
        jdbcTemplate.queryForMap(
            "SELECT type, signed_format, has_personal_data FROM documents WHERE id = ?",
            issued.documentId());
    assertThat(doc.get("type")).isEqualTo("COMMISSION_INVOICE");
    assertThat(doc.get("signed_format")).isEqualTo("XADES");
    assertThat(doc.get("has_personal_data")).isEqualTo(true);
    Integer attachments =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM document_attachments WHERE document_id = ? AND entry_id = ?",
            Integer.class,
            issued.documentId(),
            commissionEntryId);
    assertThat(attachments).isEqualTo(1);

    // Finance posted the ISS as a PAYABLE TAX_PAYABLE entry (consuming the event).
    List<Map<String, Object>> tax =
        jdbcTemplate.queryForList(
            "SELECT direction, entry_type, amount, currency FROM ledger_entries "
                + "WHERE entry_type = 'TAX_PAYABLE'");
    assertThat(tax).hasSize(1);
    assertThat(tax.get(0).get("direction")).isEqualTo("PAYABLE");
    assertThat((BigDecimal) tax.get(0).get("amount")).isEqualByComparingTo("20.25");
    assertThat(tax.get(0).get("currency")).isEqualTo("BRL");
  }

  @Test
  void reIssuingTheSameInvoiceDoesNotDuplicateNumberDocumentOrTaxPosting() {
    // BR4: idempotency — re-issuing yields ONE number, ONE document, ONE tax posting.
    UUID commissionEntryId = UUID.randomUUID();
    CommissionInvoiceView draft =
        billingService.createDraft(
            commissionEntryId, Money.of(new BigDecimal("405.00"), "BRL"), "9999999", "1.05", "dev");

    CommissionInvoiceView first = issuanceService.issue(draft.id(), "dev");
    CommissionInvoiceView second = issuanceService.issue(draft.id(), "dev"); // re-issue

    assertThat(second.number()).isEqualTo(first.number());
    assertThat(second.documentId()).isEqualTo(first.documentId());

    Integer documents =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM documents WHERE id = ?", Integer.class, first.documentId());
    assertThat(documents).isEqualTo(1);
    Integer taxEntries =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE entry_type = 'TAX_PAYABLE'", Integer.class);
    assertThat(taxEntries).isEqualTo(1);
  }

  @Test
  void municipalityRejectionIsClassifiedAs422AndNeverIssued() {
    // BR7: REJECTED → BillingMunicipalityRejectedException (422); no false "issued", nothing
    // archived.
    UUID commissionEntryId = UUID.randomUUID();
    CommissionInvoiceView draft =
        billingService.createDraft(
            commissionEntryId, Money.of(new BigDecimal("405.00"), "BRL"), "REJECT", "1.05", "dev");

    assertThatThrownBy(() -> issuanceService.issue(draft.id(), "dev"))
        .isInstanceOf(BillingMunicipalityRejectedException.class);

    CommissionInvoiceView after = billingService.getById(draft.id());
    assertThat(after.status()).isEqualTo(InvoiceStatus.RASCUNHO); // not issued
    Integer documents =
        jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class);
    assertThat(documents).isEqualTo(0);
  }

  @Test
  void webserviceTimeoutIsClassifiedAs502AndNeverIssued() {
    // BR7: TIMEOUT → BillingNfseWebserviceException (mapped to 502); no false "issued".
    UUID commissionEntryId = UUID.randomUUID();
    CommissionInvoiceView draft =
        billingService.createDraft(
            commissionEntryId, Money.of(new BigDecimal("405.00"), "BRL"), "TIMEOUT", "1.05", "dev");

    assertThatThrownBy(() -> issuanceService.issue(draft.id(), "dev"))
        .isInstanceOf(BillingNfseWebserviceException.class);

    assertThat(billingService.getById(draft.id()).status()).isEqualTo(InvoiceStatus.RASCUNHO);
  }
}
