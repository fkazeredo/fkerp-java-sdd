package com.fksoft.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.CreateBookingRequest;
import com.fksoft.application.api.dto.CreateBookingRequest.LocatorRequest;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.application.api.dto.RecordMarketRateRequest;
import com.fksoft.application.api.dto.SettlementRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.booking.BookingView;
import com.fksoft.domain.booking.LocatorOrigin;
import com.fksoft.domain.money.Money;
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
import tools.jackson.databind.JsonNode;

/**
 * End-to-end tests for the Intelligence (DSS) PromoFxAdvisor (SPEC-0013, slice 12a): consuming the
 * real cross-module events ({@code BookingConfirmed}, {@code RateSubsidyAccrued}, {@code
 * FxPositionClosed}) generates a per-agency insight with a verdict, estimated gain and provenance,
 * WITHOUT mutating the source aggregates (advises, never commands — BR2). The full event chain is
 * driven through the real REST flow (confirm booking → record settlement), proving the wiring end
 * to end against a real Postgres.
 */
class IntelligencePromoFxIntegrationTest extends AbstractPostgresIntegrationTest {

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
  void generatesConvertePromoFxInsightPerAgencyFromConsumedEventsWithProvenance() {
    UUID accountId = createAccount();
    // 6 confirmed-and-settled sales for the same agency: volume >= MIN_VOLUME (5).
    // Settled at a rate ABOVE the pinned rate, so realized drift covers the subsidy => gap >= 0.
    for (int i = 0; i < 6; i++) {
      UUID quoteId = createQuote(accountId);
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
    assertThat(page.get("totalElements").asInt()).isEqualTo(1);
    JsonNode insight = page.get("content").get(0);
    assertThat(insight.get("type").asText()).isEqualTo("PROMO_FX_ADVISOR");
    assertThat(insight.get("subjectKind").asText()).isEqualTo("AGENCY");
    assertThat(insight.get("subjectRef").asText()).isEqualTo(accountId.toString());
    assertThat(insight.get("recommendation").get("verdict").asText()).isEqualTo("CONVERTE");
    assertThat(insight.get("status").asText()).isEqualTo("NEW");
    // Provenance: the evidence cites the events that sustain the numbers (BR1/BR5).
    JsonNode sources = insight.get("evidence").get("sources");
    assertThat(sources).isNotNull();
    assertThat(sources.toString())
        .contains("RateSubsidyAccrued")
        .contains("FxPositionClosed")
        .contains("BookingConfirmed");
    assertThat(insight.get("evidence").get("volumeAttracted").asInt()).isEqualTo(6);
  }

  @Test
  void doesNotMutateSourceAggregatesWhenGeneratingInsight() {
    UUID accountId = createAccount();
    UUID quoteId = createQuote(accountId);
    BookingView booking = confirmBooking(quoteId);
    settleAbovePinned(booking.id());

    // The booking that fed the DSS is untouched: still CONFIRMED, never written back by
    // intelligence.
    BookingView after =
        restTemplate.getForEntity("/api/bookings/" + booking.id(), BookingView.class).getBody();
    assertThat(after).isNotNull();
    assertThat(after.status().name()).isEqualTo("CONFIRMED");
  }

  @Test
  void returns404ForUnknownInsight() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/intelligence/insights/" + UUID.randomUUID(), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("intelligence.insight.not-found");
  }

  private void settleAbovePinned(UUID bookingId) {
    UUID caseId = openCaseIdForBooking(bookingId);
    // Settled at 5.70 vs pinned 5.40: settlement above pinned => realized drift positive,
    // covering the subsidy so the total gap is non-negative (CONVERTE).
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

  /**
   * Finds the OPEN reconciliation case for a specific booking (cases are not ordered by booking).
   */
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
    // A market rate must exist for the FX position to open on confirmation (SPEC-0011): market
    // 5.55 vs pinned 5.40 => a subsidy is accrued; settling at 5.70 makes realized drift cover it.
    restTemplate.postForEntity(
        "/api/exchange/market-rates",
        new RecordMarketRateRequest("USD/BRL", new BigDecimal("5.55"), null),
        Void.class);
    return account.id();
  }

  private UUID createQuote(UUID accountId) {
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
