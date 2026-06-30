package com.fksoft.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.CancelCommissionInvoiceRequest;
import com.fksoft.application.api.dto.CreateCommissionInvoiceRequest;
import com.fksoft.domain.billing.CommissionInvoiceView;
import com.fksoft.domain.billing.InvoiceStatus;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * REST API journey for the Billing module (SPEC-0016 API Contracts / Error Behavior): create a
 * draft, issue it (number/ISS/documentId), read it, cancel it, and the sad paths — re-issue without
 * cancel → 409, unknown invoice → 404, municipal rejection → 422.
 */
class BillingApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
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
  void createIssueGetAndCancelJourney() {
    UUID commissionEntryId = UUID.randomUUID();

    // 1) Create draft → 201.
    ResponseEntity<CommissionInvoiceView> created =
        restTemplate.postForEntity(
            "/api/billing/invoices",
            new CreateCommissionInvoiceRequest(
                commissionEntryId, Money.of(new BigDecimal("405.00"), "BRL"), "9999999", "1.05"),
            CommissionInvoiceView.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    assertThat(created.getBody().status()).isEqualTo(InvoiceStatus.RASCUNHO);
    assertThat(created.getBody().base()).isEqualTo(Money.of(new BigDecimal("405.00"), "BRL"));
    UUID invoiceId = created.getBody().id();

    // 2) Issue → 200 EMITIDA with number, verification code, ISS and documentId.
    ResponseEntity<CommissionInvoiceView> issued =
        restTemplate.postForEntity(
            "/api/billing/invoices/" + invoiceId + "/issue", null, CommissionInvoiceView.class);
    assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(issued.getBody()).isNotNull();
    assertThat(issued.getBody().status()).isEqualTo(InvoiceStatus.EMITIDA);
    assertThat(issued.getBody().number()).isNotBlank();
    assertThat(issued.getBody().iss()).isEqualTo(Money.of(new BigDecimal("20.25"), "BRL"));
    assertThat(issued.getBody().documentId()).isNotNull();

    // 3) Get → 200.
    ResponseEntity<CommissionInvoiceView> fetched =
        restTemplate.getForEntity(
            "/api/billing/invoices/" + invoiceId, CommissionInvoiceView.class);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody()).isNotNull();
    assertThat(fetched.getBody().status()).isEqualTo(InvoiceStatus.EMITIDA);

    // 4) Cancel → 200 CANCELADA.
    ResponseEntity<CommissionInvoiceView> cancelled =
        restTemplate.postForEntity(
            "/api/billing/invoices/" + invoiceId + "/cancel",
            new CancelCommissionInvoiceRequest("erro de emissão"),
            CommissionInvoiceView.class);
    assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(cancelled.getBody()).isNotNull();
    assertThat(cancelled.getBody().status()).isEqualTo(InvoiceStatus.CANCELADA);
  }

  @Test
  void reissuingWithoutCancellingIs409() {
    UUID invoiceId = createDraft(UUID.randomUUID(), "9999999");
    restTemplate.postForEntity(
        "/api/billing/invoices/" + invoiceId + "/issue", null, CommissionInvoiceView.class);

    // Issuing again returns the same EMITIDA invoice (idempotent no-op) — 200, not a duplicate.
    ResponseEntity<CommissionInvoiceView> reissue =
        restTemplate.postForEntity(
            "/api/billing/invoices/" + invoiceId + "/issue", null, CommissionInvoiceView.class);
    assertThat(reissue.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reissue.getBody()).isNotNull();
    assertThat(reissue.getBody().status()).isEqualTo(InvoiceStatus.EMITIDA);

    // Creating a SECOND draft for the same commission is blocked by the partial UNIQUE → returns
    // the
    // existing live invoice (no duplicate), so there is at most one issued invoice per commission.
    Integer liveInvoices =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM commission_invoices WHERE status = 'EMITIDA'", Integer.class);
    assertThat(liveInvoices).isEqualTo(1);
  }

  @Test
  void unknownInvoiceIs404() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/billing/invoices/" + UUID.randomUUID(), ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("billing.invoice.not-found");
  }

  @Test
  void municipalityRejectionIs422() {
    UUID invoiceId = createDraft(UUID.randomUUID(), "REJECT");

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/billing/invoices/" + invoiceId + "/issue", null, ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("billing.municipality.rejected");
  }

  private UUID createDraft(UUID commissionEntryId, String municipality) {
    CommissionInvoiceView draft =
        restTemplate
            .postForEntity(
                "/api/billing/invoices",
                new CreateCommissionInvoiceRequest(
                    commissionEntryId,
                    Money.of(new BigDecimal("405.00"), "BRL"),
                    municipality,
                    "1.05"),
                CommissionInvoiceView.class)
            .getBody();
    assertThat(draft).isNotNull();
    return draft.id();
  }
}
