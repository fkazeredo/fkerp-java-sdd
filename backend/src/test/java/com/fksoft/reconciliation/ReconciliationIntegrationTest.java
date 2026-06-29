package com.fksoft.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.CreateBookingRequest;
import com.fksoft.application.api.dto.CreateBookingRequest.LocatorRequest;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.application.api.dto.SettlementRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.booking.BookingView;
import com.fksoft.domain.booking.LocatorOrigin;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.domain.reconciliation.CaseStatus;
import com.fksoft.domain.reconciliation.ReconciliationCaseView;
import com.fksoft.domain.reconciliation.ReconciliationService;
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
 * End-to-end tests for reconciliation (SPEC-0007): a confirmed booking opens a case (proving the
 * {@code BookingConfirmed} event flows across modules), settlement computes realized spread and FX
 * gain/loss with discrepancy flagging, booking cancellation cancels the case, opening is
 * idempotent, and an unknown case is 404.
 */
class ReconciliationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ReconciliationService reconciliationService;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM reconciliation_cases");
    jdbcTemplate.execute("DELETE FROM bookings");
    jdbcTemplate.execute("DELETE FROM override_records");
    jdbcTemplate.execute("DELETE FROM quotes");
    jdbcTemplate.execute("DELETE FROM accounts");
    jdbcTemplate.execute("DELETE FROM pinned_sell_rates");
  }

  @Test
  void opensCaseOnBookingConfirmedWithExpectedSpread() {
    confirmBooking(createQuote());

    UUID caseId = firstCaseId();
    ReconciliationCaseView view =
        restTemplate
            .getForEntity("/api/reconciliation/" + caseId, ReconciliationCaseView.class)
            .getBody();

    assertThat(view).isNotNull();
    assertThat(view.status()).isEqualTo(CaseStatus.OPEN);
    assertThat(view.expectedSpread()).isEqualTo(Money.of(new BigDecimal("135.00"), "BRL"));
  }

  @Test
  void recordsSettlementComputingRealizedSpreadAndFxGainLoss() {
    confirmBooking(createQuote());
    UUID caseId = firstCaseId();

    ResponseEntity<ReconciliationCaseView> response =
        restTemplate.postForEntity(
            "/api/reconciliation/" + caseId + "/settlement",
            new SettlementRequest(
                Money.of(new BigDecimal("3000.00"), "BRL"),
                new BigDecimal("5.700000"),
                Money.of(new BigDecimal("2850.00"), "BRL"),
                Money.of(new BigDecimal("405.00"), "BRL"),
                Money.of(new BigDecimal("270.00"), "BRL")),
            ReconciliationCaseView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ReconciliationCaseView view = response.getBody();
    assertThat(view).isNotNull();
    assertThat(view.realizedSpread()).isEqualTo(Money.of(new BigDecimal("285.00"), "BRL"));
    assertThat(view.fxGainLoss()).isEqualTo(Money.of(new BigDecimal("-150.00"), "BRL"));
    assertThat(view.status()).isEqualTo(CaseStatus.DISCREPANCY);
  }

  @Test
  void cancelsCaseWhenBookingCancelled() {
    BookingView booking = confirmBooking(createQuote());
    UUID caseId = firstCaseId();

    restTemplate.postForEntity(
        "/api/bookings/" + booking.id() + "/cancel",
        new com.fksoft.application.api.dto.CancelBookingRequest("cliente desistiu"),
        BookingView.class);

    ReconciliationCaseView view =
        restTemplate
            .getForEntity("/api/reconciliation/" + caseId, ReconciliationCaseView.class)
            .getBody();
    assertThat(view).isNotNull();
    assertThat(view.status()).isEqualTo(CaseStatus.CANCELLED);
  }

  @Test
  void opensCaseIdempotentlyPerBooking() {
    UUID quoteId = createQuote();
    BookingView booking = confirmBooking(quoteId);

    reconciliationService.openCase(booking.id(), quoteId);
    reconciliationService.openCase(booking.id(), quoteId);

    JsonNode page = restTemplate.getForEntity("/api/reconciliation", JsonNode.class).getBody();
    assertThat(page).isNotNull();
    assertThat(page.get("totalElements").asInt()).isEqualTo(1);
  }

  @Test
  void returns404ForUnknownCase() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/reconciliation/" + UUID.randomUUID(), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("reconciliation.case.not-found");
  }

  private BookingView confirmBooking(UUID quoteId) {
    BookingView booking =
        restTemplate
            .postForEntity(
                "/api/bookings",
                new CreateBookingRequest(
                    quoteId,
                    new LocatorRequest(LocatorOrigin.EXTERNAL, "ALAMO-" + UUID.randomUUID())),
                BookingView.class)
            .getBody();
    assertThat(booking).isNotNull();
    restTemplate.postForEntity(
        "/api/bookings/" + booking.id() + "/pending", null, BookingView.class);
    return restTemplate
        .postForEntity("/api/bookings/" + booking.id() + "/confirm", null, BookingView.class)
        .getBody();
  }

  private UUID firstCaseId() {
    JsonNode page = restTemplate.getForEntity("/api/reconciliation", JsonNode.class).getBody();
    assertThat(page).isNotNull();
    assertThat(page.get("content")).isNotEmpty();
    return UUID.fromString(page.get("content").get(0).get("caseId").asText());
  }

  private UUID createQuote() {
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
    QuoteView quote =
        restTemplate
            .postForEntity(
                "/api/quotes",
                new ComposeQuoteRequest(
                    account.id(),
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
