package com.fksoft.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.CreateBookingRequest;
import com.fksoft.application.api.dto.CreateBookingRequest.LocatorRequest;
import com.fksoft.application.api.dto.DecideInsightRequest;
import com.fksoft.application.api.dto.OverrideQuoteRequest;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.application.api.dto.RecordMarketRateRequest;
import com.fksoft.application.api.dto.SettlementRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.booking.BookingView;
import com.fksoft.domain.booking.LocatorOrigin;
import com.fksoft.domain.intelligence.InsightView;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the Intelligence (DSS) human-decision endpoint and the OverrideNudge seam
 * (SPEC-0013, slice 12b): recording a decision registers it WITHOUT executing any action on the
 * operation (BR4/BR2), an out-of-enum decision is rejected (400), and the OverrideNudge stays OFF
 * while the commission-tier model does not exist (feature flag off → no insight from {@code
 * PriceOverridden}, BR6/DL-0036).
 */
class IntelligenceDecisionAndNudgeIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM insights");
    jdbcTemplate.execute("DELETE FROM intelligence_agency_fx_accrual");
    jdbcTemplate.execute("DELETE FROM intelligence_booking_attribution");
    jdbcTemplate.execute("DELETE FROM fx_positions");
    jdbcTemplate.execute("DELETE FROM reconciliation_cases");
    jdbcTemplate.execute("DELETE FROM bookings");
    jdbcTemplate.execute("DELETE FROM override_records");
    jdbcTemplate.execute("DELETE FROM quotes");
    jdbcTemplate.execute("DELETE FROM accounts");
    jdbcTemplate.execute("DELETE FROM pinned_sell_rates");
    jdbcTemplate.execute("DELETE FROM market_rates");
  }

  @Test
  void recordsHumanDecisionWithoutExecutingAnyAction() {
    UUID accountId = createAccount();
    UUID insightId = generatePromoFxInsight(accountId);

    ResponseEntity<InsightView> response =
        restTemplate.postForEntity(
            "/api/intelligence/insights/" + insightId + "/decision",
            new DecideInsightRequest("ACCEPTED", "vamos manter por mais 30 dias"),
            InsightView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    InsightView decided = response.getBody();
    assertThat(decided).isNotNull();
    assertThat(decided.status().name()).isEqualTo("ACCEPTED");
    assertThat(decided.decidedBy()).isNotBlank();
    assertThat(decided.decidedAt()).isNotNull();
    // Regression (BR2): recording the decision does not change the operational state — the FX
    // position is still closed exactly as the settlement left it; no command was issued.
    Integer closedPositions =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM fx_positions WHERE status = 'CLOSED'", Integer.class);
    assertThat(closedPositions).isEqualTo(6);
  }

  @Test
  void rejectsAnOutOfEnumDecisionWith400() {
    UUID accountId = createAccount();
    UUID insightId = generatePromoFxInsight(accountId);

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/intelligence/insights/" + insightId + "/decision",
            new DecideInsightRequest("MAYBE_LATER", null),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("intelligence.decision.invalid");
  }

  @Test
  void overrideNudgeStaysOffWithoutTheTierModel() {
    UUID accountId = createAccount();
    UUID quoteId = composeManualQuote(accountId);

    // Diverge from the suggested price: publishes PriceOverridden, which the OverrideNudge listener
    // consumes — but the feature flag is off (no tier model), so NO insight is generated (BR6).
    restTemplate.postForEntity(
        "/api/quotes/" + quoteId + "/override",
        new OverrideQuoteRequest(Money.of(new BigDecimal("2500.00"), "BRL"), "cliente fidelizado"),
        QuoteView.class);

    JsonNode page =
        restTemplate
            .getForEntity("/api/intelligence/insights?type=OVERRIDE_NUDGE", JsonNode.class)
            .getBody();
    assertThat(page).isNotNull();
    assertThat(page.get("totalElements").asInt()).isEqualTo(0);
  }

  /**
   * Drives 6 confirmed-and-settled sales for one agency to produce one CONVERTE PromoFx insight.
   */
  private UUID generatePromoFxInsight(UUID accountId) {
    for (int i = 0; i < 6; i++) {
      UUID quoteId = composeManualQuote(accountId);
      BookingView booking = confirmBooking(quoteId);
      settleAbovePinned(booking.id());
    }
    JsonNode page =
        restTemplate
            .getForEntity(
                "/api/intelligence/insights?type=PROMO_FX_ADVISOR&subjectRef=" + accountId,
                JsonNode.class)
            .getBody();
    assertThat(page).isNotNull();
    assertThat(page.get("content")).isNotEmpty();
    return UUID.fromString(page.get("content").get(0).get("id").asText());
  }

  private void settleAbovePinned(UUID bookingId) {
    UUID caseId = openCaseIdForBooking(bookingId);
    restTemplate.postForEntity(
        "/api/reconciliation/" + caseId + "/settlement",
        new SettlementRequest(
            Money.of(new BigDecimal("3000.00"), "BRL"),
            new BigDecimal("5.700000"),
            Money.of(new BigDecimal("2850.00"), "BRL"),
            Money.of(new BigDecimal("405.00"), "BRL"),
            Money.of(new BigDecimal("270.00"), "BRL")),
        Void.class);
  }

  private UUID openCaseIdForBooking(UUID bookingId) {
    JsonNode page =
        restTemplate
            .getForEntity("/api/reconciliation?status=OPEN&size=100", JsonNode.class)
            .getBody();
    assertThat(page).isNotNull();
    for (JsonNode caseNode : page.get("content")) {
      if (bookingId.toString().equals(caseNode.get("bookingId").asText())) {
        return UUID.fromString(caseNode.get("caseId").asText());
      }
    }
    throw new AssertionError("no OPEN reconciliation case for booking " + bookingId);
  }

  private BookingView confirmBooking(UUID quoteId) {
    BookingView booking =
        restTemplate
            .postForEntity(
                "/api/bookings",
                new CreateBookingRequest(
                    quoteId,
                    new LocatorRequest(LocatorOrigin.EXTERNAL, "ALAMO-" + UUID.randomUUID()),
                    null),
                BookingView.class)
            .getBody();
    assertThat(booking).isNotNull();
    restTemplate.postForEntity(
        "/api/bookings/" + booking.id() + "/pending", null, BookingView.class);
    return restTemplate
        .postForEntity("/api/bookings/" + booking.id() + "/confirm", null, BookingView.class)
        .getBody();
  }

  private UUID createAccount() {
    AccountResponse account =
        restTemplate
            .postForEntity(
                "/api/accounts",
                new CreateAccountRequest(
                    LegalType.CNPJ, "12345678000195", "Agência Sol e Mar", null, null),
                AccountResponse.class)
            .getBody();
    assertThat(account).isNotNull();
    restTemplate.postForEntity(
        "/api/exchange/pinned-rates",
        new PinRateRequest("USD/BRL", new BigDecimal("5.40"), null, null),
        Void.class);
    restTemplate.postForEntity(
        "/api/exchange/market-rates",
        new RecordMarketRateRequest("USD/BRL", new BigDecimal("5.55"), null),
        Void.class);
    return account.id();
  }

  private UUID composeManualQuote(UUID accountId) {
    QuoteView quote =
        restTemplate
            .postForEntity(
                "/api/quotes",
                new ComposeQuoteRequest(
                    accountId,
                    Money.of(new BigDecimal("500.00"), "USD"),
                    "USD/BRL",
                    new BigDecimal("0.15"),
                    new BigDecimal("0.10"),
                    null),
                QuoteView.class)
            .getBody();
    assertThat(quote).isNotNull();
    return quote.id();
  }
}
