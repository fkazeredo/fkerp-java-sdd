package com.fksoft.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AttachDocumentRequest;
import com.fksoft.application.api.dto.CreateLedgerEntryRequest;
import com.fksoft.application.api.dto.CreateLedgerEntryRequest.PartyRequest;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.finance.EntryTypeCodes;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.LedgerEntryView;
import com.fksoft.domain.finance.PartyTypeCodes;
import com.fksoft.domain.finance.PeriodStatus;
import com.fksoft.domain.finance.PeriodView;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
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
 * The golden-rule regression of Phase 2 (SPEC-0008 BR6 + SPEC-0015 BR3): a financial entry without
 * its mandatory document <strong>blocks</strong> the monthly close, and the same period
 * <strong>closes</strong> once the document is attached. This crosses Finance (the period machine
 * and the veto) and Compliance (the document rule), proving the veto end to end.
 */
class CloseVetoIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM document_attachments");
    jdbcTemplate.execute("DELETE FROM documents");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
  }

  @Test
  void entryWithoutDocumentBlocksTheCloseThenClosesOnceAttached() {
    UUID entryId = createCommissionReceivable("2026-06");

    // 1) Without the document, the month does not close (the veto).
    ResponseEntity<ApiErrorResponse> blocked =
        restTemplate.postForEntity(
            "/api/finance/periods/2026-06/close", null, ApiErrorResponse.class);
    assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(blocked.getBody()).isNotNull();
    assertThat(blocked.getBody().code()).isEqualTo("finance.period.cannot-close");
    assertThat(blocked.getBody().fields()).anyMatch(f -> f.field().equals(entryId.toString()));

    // The period was not sealed — it returned to OPEN.
    PeriodView afterVeto =
        restTemplate.getForEntity("/api/finance/periods/2026-06", PeriodView.class).getBody();
    assertThat(afterVeto).isNotNull();
    assertThat(afterVeto.status()).isEqualTo(PeriodStatus.OPEN);

    // 2) Attach the required COMMISSION_INVOICE.
    DocumentView document = uploadCommissionInvoice();
    restTemplate.postForEntity(
        "/api/compliance/documents/" + document.id() + "/attach",
        new AttachDocumentRequest(entryId, "COMMISSION_RECEIVABLE"),
        Void.class);

    // 3) Now the same period closes.
    ResponseEntity<PeriodView> closed =
        restTemplate.postForEntity("/api/finance/periods/2026-06/close", null, PeriodView.class);
    assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(closed.getBody()).isNotNull();
    assertThat(closed.getBody().status()).isEqualTo(PeriodStatus.CLOSED);
  }

  private UUID createCommissionReceivable(String period) {
    LedgerEntryView entry =
        restTemplate
            .postForEntity(
                "/api/finance/entries",
                new CreateLedgerEntryRequest(
                    LedgerDirection.RECEIVABLE,
                    new PartyRequest("ag-1", PartyTypeCodes.AGENCY),
                    Money.of(new BigDecimal("2700.00"), "BRL"),
                    EntryTypeCodes.COMMISSION_RECEIVABLE,
                    period),
                LedgerEntryView.class)
            .getBody();
    assertThat(entry).isNotNull();
    return entry.id();
  }

  private DocumentView uploadCommissionInvoice() {
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", filePart("comissao.pdf", "fatura-de-comissao".getBytes()));
    body.add("type", "COMMISSION_INVOICE");
    body.add("issuedAt", "2026-06-20");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    DocumentView document =
        restTemplate
            .postForEntity(
                "/api/compliance/documents", new HttpEntity<>(body, headers), DocumentView.class)
            .getBody();
    assertThat(document).isNotNull();
    return document;
  }

  private static ByteArrayResource filePart(String filename, byte[] content) {
    return new ByteArrayResource(content) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }
}
