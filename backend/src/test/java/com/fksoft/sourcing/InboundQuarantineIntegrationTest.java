package com.fksoft.sourcing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.quoting.PriceOrigin;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.domain.sourcing.InboundQuarantineStatus;
import com.fksoft.domain.sourcing.InboundQuarantineView;
import com.fksoft.infra.integration.quotationsite.QuotationSiteSignatureVerifier;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests of the inbound quarantine (SPEC-0009 BR10, DL-0120 — revises DL-0017) against
 * real Postgres. The exception-queue behavior: a business rejection (unknown account) still answers
 * 422 (the DL-0017 wire contract) but the payload is KEPT for operator replay; a re-delivery keeps
 * a single pending entry; replay after fixing the cause creates the INTEGRATED quote and marks the
 * entry REPLAYED; replay while the cause persists keeps it pending; discard closes it; and a
 * signature failure quarantines NOTHING (unauthenticated payloads never persist).
 */
class InboundQuarantineIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Value("${integration.quotation-site.secret:dev-quotation-site-secret}")
  private String secret;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM inbound_quarantine");
    jdbcTemplate.execute("DELETE FROM inbound_quotations");
    jdbcTemplate.execute("DELETE FROM override_records");
    jdbcTemplate.execute("DELETE FROM quotes");
    jdbcTemplate.execute("DELETE FROM sourced_offers");
    jdbcTemplate.execute("DELETE FROM accounts");
  }

  @Test
  void rejectedInboundIsQuarantinedAndReDeliveryKeepsASinglePendingEntry() {
    String body = payload("QS-Q-001", "99999999000191");

    ResponseEntity<ApiErrorResponse> rejected = postInbound(body);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(rejected.getBody()).isNotNull();
    assertThat(rejected.getBody().code()).isEqualTo("integration.account.not-found");

    // The payload survived the 422 in quarantine (own transaction — BR10).
    List<InboundQuarantineView> pending = listQuarantine();
    assertThat(pending).hasSize(1);
    assertThat(pending.get(0).status()).isEqualTo(InboundQuarantineStatus.QUARANTINED);
    assertThat(pending.get(0).externalQuotationId()).isEqualTo("QS-Q-001");
    assertThat(pending.get(0).reasonCode()).isEqualTo("integration.account.not-found");

    // A re-delivery of the same rejected payload keeps the single pending entry.
    postInbound(body);
    assertThat(listQuarantine()).hasSize(1);
  }

  @Test
  void replayAfterFixingTheCauseCreatesTheQuoteAndMarksReplayed() {
    postInbound(payload("QS-Q-002", "12345678000195"));
    UUID entryId = listQuarantine().get(0).id();

    // Fix the cause: register the account the payload references.
    createAccount("12345678000195");

    ResponseEntity<InboundQuarantineView> replayed =
        restTemplate.postForEntity(
            "/api/sourcing/inbound-quarantine/" + entryId + "/replay",
            null,
            InboundQuarantineView.class);

    assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(replayed.getBody()).isNotNull();
    assertThat(replayed.getBody().status()).isEqualTo(InboundQuarantineStatus.REPLAYED);
    UUID quoteId = replayed.getBody().replayedQuoteId();
    assertThat(quoteId).isNotNull();

    // The replay went through the normal INTEGRATED branch.
    ResponseEntity<QuoteView> quote =
        restTemplate.getForEntity("/api/quotes/" + quoteId, QuoteView.class);
    assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(quote.getBody()).isNotNull();
    assertThat(quote.getBody().priceOrigin()).isEqualTo(PriceOrigin.INTEGRATED);

    // Replaying a resolved entry is a conflict (409).
    ResponseEntity<ApiErrorResponse> again =
        restTemplate.postForEntity(
            "/api/sourcing/inbound-quarantine/" + entryId + "/replay",
            null,
            ApiErrorResponse.class);
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(again.getBody()).isNotNull();
    assertThat(again.getBody().code()).isEqualTo("sourcing.quarantine.not-pending");
  }

  @Test
  void replayWhileTheCauseStillPersistsKeepsTheEntryPending() {
    postInbound(payload("QS-Q-003", "99999999000191"));
    UUID entryId = listQuarantine().get(0).id();

    ResponseEntity<ApiErrorResponse> stillRejected =
        restTemplate.postForEntity(
            "/api/sourcing/inbound-quarantine/" + entryId + "/replay",
            null,
            ApiErrorResponse.class);

    assertThat(stillRejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(stillRejected.getBody()).isNotNull();
    assertThat(stillRejected.getBody().code()).isEqualTo("integration.account.not-found");

    List<InboundQuarantineView> entries = listQuarantine();
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).status()).isEqualTo(InboundQuarantineStatus.QUARANTINED);
  }

  @Test
  void discardClosesTheEntryAndItCannotBeReplayed() {
    postInbound(payload("QS-Q-004", "99999999000191"));
    UUID entryId = listQuarantine().get(0).id();

    ResponseEntity<InboundQuarantineView> discarded =
        restTemplate.postForEntity(
            "/api/sourcing/inbound-quarantine/" + entryId + "/discard",
            null,
            InboundQuarantineView.class);
    assertThat(discarded.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(discarded.getBody()).isNotNull();
    assertThat(discarded.getBody().status()).isEqualTo(InboundQuarantineStatus.DISCARDED);

    ResponseEntity<ApiErrorResponse> replayAfterDiscard =
        restTemplate.postForEntity(
            "/api/sourcing/inbound-quarantine/" + entryId + "/replay",
            null,
            ApiErrorResponse.class);
    assertThat(replayAfterDiscard.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void signatureFailureQuarantinesNothing() {
    String body = payload("QS-Q-005", "99999999000191");

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.exchange(
            "/api/integration/quotation-site/inbound",
            HttpMethod.POST,
            entity(body, "sha256=deadbeef"),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    Integer count =
        jdbcTemplate.queryForObject("SELECT count(*) FROM inbound_quarantine", Integer.class);
    assertThat(count).isZero();
  }

  // --- helpers ---

  private ResponseEntity<ApiErrorResponse> postInbound(String body) {
    return restTemplate.exchange(
        "/api/integration/quotation-site/inbound",
        HttpMethod.POST,
        entity(body, sign(body)),
        ApiErrorResponse.class);
  }

  private List<InboundQuarantineView> listQuarantine() {
    ResponseEntity<List<InboundQuarantineView>> response =
        restTemplate.exchange(
            "/api/sourcing/inbound-quarantine?status=QUARANTINED",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private static HttpEntity<String> entity(String body, String signature) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Signature", signature);
    return new HttpEntity<>(body, headers);
  }

  private String sign(String body) {
    return new QuotationSiteSignatureVerifier(secret).sign(body.getBytes(StandardCharsets.UTF_8));
  }

  private static String payload(String externalId, String document) {
    return "{ \"externalQuotationId\":\""
        + externalId
        + "\", \"product\":\"City Tour Rio - full day\","
        + " \"price\":{\"amount\":\"480.00\",\"currency\":\"BRL\"},"
        + " \"account\":{\"document\":\""
        + document
        + "\"} }";
  }

  private void createAccount(String document) {
    restTemplate.postForEntity(
        "/api/accounts",
        new CreateAccountRequest(LegalType.CNPJ, document, "Agência Sol e Mar", null, null),
        Void.class);
  }
}
