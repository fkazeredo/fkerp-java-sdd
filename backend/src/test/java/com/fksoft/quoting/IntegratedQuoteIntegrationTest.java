package com.fksoft.quoting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.OverrideQuoteRequest;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.PriceOrigin;
import com.fksoft.domain.quoting.QuoteIntegrationPort;
import com.fksoft.domain.quoting.QuoteStatus;
import com.fksoft.domain.quoting.QuoteView;
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
 * End-to-end tests for the INTEGRATED branch of the Quote (SPEC-0009 BR2, DL-0018) against real
 * Postgres, driving the {@link QuoteIntegrationPort} directly (the ACL wires the webhook to it in
 * Slice 8c). Proves: the suggestion engine does not run ({@code suggested == applied == external},
 * no commission/markup/FX), no override exists, and an override on an INTEGRATED quote is refused
 * (409). This is the boundary regression that the integrated branch trusts the external price.
 */
class IntegratedQuoteIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private QuoteIntegrationPort quoteIntegrationPort;
  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM override_records");
    jdbcTemplate.execute("DELETE FROM quotes");
  }

  @Test
  void createsIntegratedQuoteTrustingTheExternalPriceWithoutSuggestionOrOverride() {
    UUID accountId = UUID.randomUUID();
    UUID quoteId =
        quoteIntegrationPort.createIntegratedQuote(
            accountId,
            UUID.randomUUID(),
            Money.of(new BigDecimal("480.00"), "BRL"),
            null,
            "connector");

    ResponseEntity<QuoteView> response =
        restTemplate.getForEntity("/api/quotes/" + quoteId, QuoteView.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    QuoteView quote = response.getBody();
    assertThat(quote).isNotNull();
    assertThat(quote.priceOrigin()).isEqualTo(PriceOrigin.INTEGRATED);
    assertThat(quote.status()).isEqualTo(QuoteStatus.COMPOSED);
    assertThat(quote.suggestedAmount()).isEqualTo(Money.of(new BigDecimal("480.00"), "BRL"));
    assertThat(quote.appliedAmount()).isEqualTo(Money.of(new BigDecimal("480.00"), "BRL"));
    // The suggestion engine did not run: no commission, markup, FX or override (BR2).
    assertThat(quote.commission()).isNull();
    assertThat(quote.markup()).isNull();
    assertThat(quote.fxRate()).isNull();
    assertThat(quote.baseConverted()).isNull();
    assertThat(quote.overrides()).isEmpty();
  }

  @Test
  void refusesOverrideOnIntegratedQuoteWith409() {
    UUID quoteId =
        quoteIntegrationPort.createIntegratedQuote(
            UUID.randomUUID(), null, Money.of(new BigDecimal("480.00"), "BRL"), null, "connector");

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/quotes/" + quoteId + "/override",
            new OverrideQuoteRequest(Money.of(new BigDecimal("450.00"), "BRL"), "tentativa"),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("quoting.override.not-applicable");
  }
}
