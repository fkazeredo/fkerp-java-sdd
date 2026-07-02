package com.fksoft.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AttachDocumentRequest;
import com.fksoft.application.api.dto.CreateLedgerEntryRequest;
import com.fksoft.application.api.dto.CreateLedgerEntryRequest.PartyRequest;
import com.fksoft.domain.compliance.CloseCheckView;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.LedgerEntryView;
import com.fksoft.domain.finance.PartyTypeCodes;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * End-to-end tests for the Compliance module (SPEC-0008): upload a document (201), reject an
 * invalid upload (400), attach idempotently, the period close-check returning {@code
 * canClose=false} with the pending entries, and purge rejected within retention (409). It also
 * proves the full vault flow: attaching the required document makes the entry conformant so the
 * close-check passes.
 */
class ComplianceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private com.fksoft.domain.compliance.ComplianceService complianceService;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM document_attachments");
    jdbcTemplate.execute("DELETE FROM documents");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
  }

  @Test
  void uploadsADocumentAndComputesRetention() {
    DocumentView document = uploadNfse("2026-06-20", null, null);

    assertThat(document).isNotNull();
    assertThat(document.hash()).startsWith("sha256:");
    assertThat(document.retentionUntil()).isEqualTo(LocalDate.of(2031, 6, 20));
  }

  @Test
  void rejectsAnInvalidUpload() {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", emptyFilePart());
    body.add("type", "NFSE");
    body.add("issuedAt", "2026-06-20");

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/compliance/documents", multipart(body), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("compliance.upload.invalid");
  }

  @Test
  void attachIsIdempotent() {
    UUID entryId = createEntry("COMMISSION_RECEIVABLE", "2026-06");
    DocumentView document = uploadCommissionInvoice();

    ResponseEntity<Void> first =
        restTemplate.postForEntity(
            "/api/compliance/documents/" + document.id() + "/attach",
            new AttachDocumentRequest(entryId, "COMMISSION_RECEIVABLE"),
            Void.class);
    ResponseEntity<Void> second =
        restTemplate.postForEntity(
            "/api/compliance/documents/" + document.id() + "/attach",
            new AttachDocumentRequest(entryId, "COMMISSION_RECEIVABLE"),
            Void.class);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM document_attachments WHERE entry_id = ?", Integer.class, entryId);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void closeCheckReturnsCannotCloseWithPendingEntries() {
    UUID entryId = createEntry("COMMISSION_RECEIVABLE", "2026-06");

    CloseCheckView check =
        restTemplate
            .getForEntity("/api/compliance/close-check?period=2026-06", CloseCheckView.class)
            .getBody();

    assertThat(check).isNotNull();
    assertThat(check.canClose()).isFalse();
    assertThat(check.pending()).hasSize(1);
    assertThat(check.pending().get(0).entryId()).isEqualTo(entryId);
    assertThat(check.pending().get(0).missing()).contains("COMMISSION_INVOICE");
  }

  @Test
  void attachingTheRequiredDocumentClearsThePending() {
    UUID entryId = createEntry("COMMISSION_RECEIVABLE", "2026-06");
    DocumentView document = uploadCommissionInvoice();
    restTemplate.postForEntity(
        "/api/compliance/documents/" + document.id() + "/attach",
        new AttachDocumentRequest(entryId, "COMMISSION_RECEIVABLE"),
        Void.class);

    CloseCheckView check =
        restTemplate
            .getForEntity("/api/compliance/close-check?period=2026-06", CloseCheckView.class)
            .getBody();

    assertThat(check).isNotNull();
    assertThat(check.canClose()).isTrue();
    assertThat(check.pending()).isEmpty();
  }

  @Test
  void flagsDocumentsApproachingRetention() {
    uploadNfse("2026-06-20", null, null); // retention until 2031-06-20

    // A huge horizon includes the document; a tiny one excludes it (deadline is years away).
    int flaggedFar = complianceService.flagRetentionExpiring(10_000);
    int flaggedNear = complianceService.flagRetentionExpiring(1);

    assertThat(flaggedFar).isEqualTo(1);
    assertThat(flaggedNear).isZero();
  }

  @Test
  void purgeIsRejectedWithinRetention() {
    DocumentView document = uploadNfse("2026-06-20", null, null);

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.exchange(
            "/api/compliance/documents/" + document.id(),
            org.springframework.http.HttpMethod.DELETE,
            null,
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("compliance.retention.not-expired");
  }

  private DocumentView uploadNfse(String issuedAt, UUID entryId, String entryType) {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart("nota.pdf", "conteudo-da-nota".getBytes()));
    body.add("type", "NFSE");
    body.add("issuedAt", issuedAt);
    if (entryId != null) {
      body.add("entryId", entryId.toString());
      body.add("entryType", entryType);
    }
    DocumentView document =
        restTemplate
            .postForEntity("/api/compliance/documents", multipart(body), DocumentView.class)
            .getBody();
    assertThat(document).isNotNull();
    return document;
  }

  private DocumentView uploadCommissionInvoice() {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart("comissao.pdf", "fatura-de-comissao".getBytes()));
    body.add("type", "COMMISSION_INVOICE");
    body.add("issuedAt", "2026-06-20");
    DocumentView document =
        restTemplate
            .postForEntity("/api/compliance/documents", multipart(body), DocumentView.class)
            .getBody();
    assertThat(document).isNotNull();
    return document;
  }

  private UUID createEntry(String entryType, String period) {
    LedgerEntryView entry =
        restTemplate
            .postForEntity(
                "/api/finance/entries",
                new CreateLedgerEntryRequest(
                    LedgerDirection.RECEIVABLE,
                    new PartyRequest("ag-1", PartyTypeCodes.AGENCY),
                    Money.of(new BigDecimal("2700.00"), "BRL"),
                    entryType,
                    period),
                LedgerEntryView.class)
            .getBody();
    assertThat(entry).isNotNull();
    return entry.id();
  }

  private static HttpEntity<MultiValueMap<String, Object>> multipart(
      MultiValueMap<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    return new HttpEntity<>(body, headers);
  }

  private static ByteArrayResource filePart(String filename, byte[] content) {
    return new ByteArrayResource(content) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }

  private static ByteArrayResource emptyFilePart() {
    return new ByteArrayResource(new byte[0]) {
      @Override
      public String getFilename() {
        return "empty.pdf";
      }
    };
  }
}
