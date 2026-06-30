package com.fksoft.sourcing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.PriceOrigin;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.domain.sourcing.ConnectorHealthView;
import com.fksoft.domain.sourcing.InboundQuotationResult;
import com.fksoft.infra.integration.quotationsite.QuotationSiteSignatureVerifier;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the inbound ACL of the quotation-site webhook (SPEC-0009) against real
 * Postgres. The external site is out of scope, so the test signs the body with the same shared
 * secret (DL-0016) — proving the ACL translation and the INTEGRATED branch end to end. Covers: a
 * valid webhook creates a Quote INTEGRATED (202); idempotent re-delivery returns the same quoteId;
 * an invalid signature is rejected (401) and nothing is created; an invalid payload is rejected
 * (400); an unknown account document is rejected (422); the connector health endpoint.
 */
class QuotationSiteInboundIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Value("${integration.quotation-site.secret:dev-quotation-site-secret}")
  private String secret;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM inbound_quotations");
    jdbcTemplate.execute("DELETE FROM override_records");
    jdbcTemplate.execute("DELETE FROM quotes");
    jdbcTemplate.execute("DELETE FROM sourced_offers");
    jdbcTemplate.execute("DELETE FROM accounts");
  }

  @Test
  void validWebhookCreatesAnIntegratedQuoteWithoutSuggestionOrOverride() {
    createAccount("12345678000195");
    String body = payload("QS-2026-555", "12345678000195");

    ResponseEntity<InboundQuotationResult> response = post(body, sign(body));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    InboundQuotationResult result = response.getBody();
    assertThat(result).isNotNull();
    assertThat(result.priceOrigin()).isEqualTo("INTEGRATED");
    assertThat(result.appliedAmount()).isEqualTo(Money.of(new BigDecimal("480.00"), "BRL"));

    // The created quote is INTEGRATED: applied == external price, no commission/markup/override.
    ResponseEntity<QuoteView> quote =
        restTemplate.getForEntity("/api/quotes/" + result.quoteId(), QuoteView.class);
    assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(quote.getBody()).isNotNull();
    assertThat(quote.getBody().priceOrigin()).isEqualTo(PriceOrigin.INTEGRATED);
    assertThat(quote.getBody().appliedAmount())
        .isEqualTo(Money.of(new BigDecimal("480.00"), "BRL"));
    assertThat(quote.getBody().commission()).isNull();
    assertThat(quote.getBody().overrides()).isEmpty();
  }

  @Test
  void reDeliveryOfTheSameExternalIdIsIdempotent() {
    createAccount("12345678000195");
    String body = payload("QS-2026-777", "12345678000195");

    InboundQuotationResult first = post(body, sign(body)).getBody();
    InboundQuotationResult second = post(body, sign(body)).getBody();

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(second.quoteId()).isEqualTo(first.quoteId());
    Integer quoteCount = jdbcTemplate.queryForObject("SELECT count(*) FROM quotes", Integer.class);
    assertThat(quoteCount).isEqualTo(1);
  }

  @Test
  void invalidSignatureIsRejectedAndNothingIsCreated() {
    createAccount("12345678000195");
    String body = payload("QS-2026-888", "12345678000195");

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.exchange(
            "/api/integration/quotation-site/inbound",
            HttpMethod.POST,
            entity(body, "deadbeef"),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("integration.signature.invalid");
    Integer quoteCount = jdbcTemplate.queryForObject("SELECT count(*) FROM quotes", Integer.class);
    assertThat(quoteCount).isZero();
  }

  @Test
  void invalidPayloadIsRejectedWith400() {
    String body = "{ \"externalQuotationId\":\"QS-1\" }";

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.exchange(
            "/api/integration/quotation-site/inbound",
            HttpMethod.POST,
            entity(body, sign(body)),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("integration.payload.invalid");
  }

  @Test
  void unknownAccountDocumentIsRejectedWith422() {
    String body = payload("QS-2026-999", "99999999000191");

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.exchange(
            "/api/integration/quotation-site/inbound",
            HttpMethod.POST,
            entity(body, sign(body)),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("integration.account.not-found");
    Integer quoteCount = jdbcTemplate.queryForObject("SELECT count(*) FROM quotes", Integer.class);
    assertThat(quoteCount).isZero();
  }

  @Test
  void connectorHealthReportsProcessedInboundQuotations() {
    createAccount("12345678000195");
    String body = payload("QS-2026-1000", "12345678000195");
    post(body, sign(body));

    ResponseEntity<ConnectorHealthView> health =
        restTemplate.getForEntity(
            "/api/integration/quotation-site/health", ConnectorHealthView.class);

    assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(health.getBody()).isNotNull();
    assertThat(health.getBody().connector()).isEqualTo("quotation-site");
    assertThat(health.getBody().status()).isEqualTo("UP");
    assertThat(health.getBody().inboundQuotationsTotal()).isEqualTo(1);
    assertThat(health.getBody().lastReceivedAt()).isNotNull();
  }

  private ResponseEntity<InboundQuotationResult> post(String body, String signature) {
    return restTemplate.exchange(
        "/api/integration/quotation-site/inbound",
        HttpMethod.POST,
        entity(body, signature),
        InboundQuotationResult.class);
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
