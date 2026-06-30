package com.fksoft.exchange;

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
import com.fksoft.domain.exchange.FxPositionStatus;
import com.fksoft.domain.exchange.FxPositionView;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.domain.reconciliation.ReconciliationCaseView;
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
 * End-to-end tests for the FX-position lifecycle (SPEC-0011 BR2-BR5, DL-0028). A confirmed sale
 * with a foreign-currency cost opens a position accruing the subsidy (the OVERVIEW 7.2 example: USD
 * 1000, pinned 5.40, market-at-freeze 5.55 → subsidy 150); recording the supplier settlement (5.70)
 * closes it with realizedDrift 150 and totalGap 300. Regression: the position's totalGap equals the
 * negative of Reconciliation's per-case fxGainLoss (same settlement rate, no duplicated math).
 */
class FxPositionIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
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
  void opensPositionAccruingSubsidyOnConfirmation() {
    BookingView booking = confirmCanonicalSale();

    FxPositionView position =
        restTemplate
            .getForEntity("/api/exchange/positions/" + booking.id(), FxPositionView.class)
            .getBody();

    assertThat(position).isNotNull();
    assertThat(position.status()).isEqualTo(FxPositionStatus.OPEN);
    // subsidy = (5.55 − 5.40) × 1000 = 150.00 (OVERVIEW 7.2)
    assertThat(position.subsidy()).isEqualTo(Money.of(new BigDecimal("150.00"), "BRL"));
    assertThat(position.foreignAmount()).isEqualTo(Money.of(new BigDecimal("1000.00"), "USD"));
    assertThat(position.pinnedRate()).isEqualByComparingTo("5.400000");
    assertThat(position.marketAtFreeze()).isEqualByComparingTo("5.550000");
  }

  @Test
  void closesPositionWithRealizedDriftAndTotalGapOnSettlement() {
    BookingView booking = confirmCanonicalSale();
    UUID caseId = firstCaseId();

    // settle the reconciliation case at 5.70 → publishes SpreadRealized → closes the FX position.
    restTemplate.postForEntity(
        "/api/reconciliation/" + caseId + "/settlement",
        new SettlementRequest(
            Money.of(new BigDecimal("6000.00"), "BRL"),
            new BigDecimal("5.700000"),
            Money.of(new BigDecimal("5700.00"), "BRL"),
            Money.of(new BigDecimal("810.00"), "BRL"),
            Money.of(new BigDecimal("540.00"), "BRL")),
        ReconciliationCaseView.class);

    FxPositionView position =
        restTemplate
            .getForEntity("/api/exchange/positions/" + booking.id(), FxPositionView.class)
            .getBody();

    assertThat(position).isNotNull();
    assertThat(position.status()).isEqualTo(FxPositionStatus.CLOSED);
    // realizedDrift = (5.70 − 5.55) × 1000 = 150.00 ; totalGap = 150 + 150 = 300.00
    assertThat(position.realizedDrift()).isEqualTo(Money.of(new BigDecimal("150.00"), "BRL"));
    assertThat(position.totalGap()).isEqualTo(Money.of(new BigDecimal("300.00"), "BRL"));
    assertThat(position.settlementRate()).isEqualByComparingTo("5.700000");
  }

  @Test
  void totalGapMatchesReconciliationFxGainLossSign() {
    BookingView booking = confirmCanonicalSale();
    UUID caseId = firstCaseId();

    ReconciliationCaseView settled =
        restTemplate
            .postForEntity(
                "/api/reconciliation/" + caseId + "/settlement",
                new SettlementRequest(
                    Money.of(new BigDecimal("6000.00"), "BRL"),
                    new BigDecimal("5.700000"),
                    Money.of(new BigDecimal("5700.00"), "BRL"),
                    Money.of(new BigDecimal("810.00"), "BRL"),
                    Money.of(new BigDecimal("540.00"), "BRL")),
                ReconciliationCaseView.class)
            .getBody();
    FxPositionView position =
        restTemplate
            .getForEntity("/api/exchange/positions/" + booking.id(), FxPositionView.class)
            .getBody();

    assertThat(settled).isNotNull();
    assertThat(position).isNotNull();
    // Reconciliation BR5: fxGainLoss = (pinned − settlement) × amount = (5.40 − 5.70) × 1000 =
    // -300.
    assertThat(settled.fxGainLoss()).isEqualTo(Money.of(new BigDecimal("-300.00"), "BRL"));
    // Exchange totalGap = (settlement − pinned) × amount = +300 → the negative of fxGainLoss.
    assertThat(position.totalGap().amount())
        .isEqualByComparingTo(settled.fxGainLoss().amount().negate());
  }

  @Test
  void doesNotOpenAPositionWithoutAMarketRate() {
    // No market rate recorded: confirmation still works, but no FX position is opened.
    AccountResponse account = createAccount();
    pinSellRate();
    UUID quoteId = composeQuote(account.id());
    BookingView booking = confirmBooking(quoteId);

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/exchange/positions/" + booking.id(), ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("exchange.position.not-found");
  }

  @Test
  void returns404ForUnknownBooking() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/exchange/positions/" + UUID.randomUUID(), ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("exchange.position.not-found");
  }

  private BookingView confirmCanonicalSale() {
    AccountResponse account = createAccount();
    pinSellRate();
    recordMarketRate();
    UUID quoteId = composeQuote(account.id());
    return confirmBooking(quoteId);
  }

  private AccountResponse createAccount() {
    AccountResponse account =
        restTemplate
            .postForEntity(
                "/api/accounts",
                new CreateAccountRequest(
                    LegalType.CNPJ, "12345678000195", "Agência Sol e Mar", null, null),
                AccountResponse.class)
            .getBody();
    assertThat(account).isNotNull();
    return account;
  }

  private void pinSellRate() {
    restTemplate.postForEntity(
        "/api/exchange/pinned-rates",
        new PinRateRequest("USD/BRL", new BigDecimal("5.40"), null, null),
        Void.class);
  }

  private void recordMarketRate() {
    restTemplate.postForEntity(
        "/api/exchange/market-rates",
        new RecordMarketRateRequest("USD/BRL", new BigDecimal("5.55"), null),
        Void.class);
  }

  private UUID composeQuote(UUID accountId) {
    QuoteView quote =
        restTemplate
            .postForEntity(
                "/api/quotes",
                new ComposeQuoteRequest(
                    accountId,
                    Money.of(new BigDecimal("1000.00"), "USD"),
                    "USD/BRL",
                    new BigDecimal("0.15"),
                    new BigDecimal("0.10"),
                    null),
                QuoteView.class)
            .getBody();
    assertThat(quote).isNotNull();
    return quote.id();
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

  private UUID firstCaseId() {
    JsonNode page = restTemplate.getForEntity("/api/reconciliation", JsonNode.class).getBody();
    assertThat(page).isNotNull();
    assertThat(page.get("content")).isNotEmpty();
    return UUID.fromString(page.get("content").get(0).get("caseId").asText());
  }
}
