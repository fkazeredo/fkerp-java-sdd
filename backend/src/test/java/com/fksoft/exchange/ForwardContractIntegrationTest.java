package com.fksoft.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.CreateBookingRequest;
import com.fksoft.application.api.dto.CreateBookingRequest.LocatorRequest;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.application.api.dto.RecordMarketRateRequest;
import com.fksoft.application.api.dto.RegisterForwardRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.booking.BookingView;
import com.fksoft.domain.booking.LocatorOrigin;
import com.fksoft.domain.exchange.ForwardContractView;
import com.fksoft.domain.exchange.ForwardStatus;
import com.fksoft.domain.exchange.LiveExposureView;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for FX forward contracts (SPEC-0032, Fase 19h/DL-0130) against real Postgres:
 * the lifecycle (register → settle | cancel, with 409 on a resolved contract and 400 on invalid
 * input) and the <strong>coverage effect</strong> — an OPEN forward reduces the UNHEDGED exposure
 * base, so a drift that alerted on the uncovered book stops alerting once the book is hedged
 * (DL-0027 revised).
 */
class ForwardContractIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM fx_forward_contracts");
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
  void registersSettlesAndReportsTheRealizedResult() {
    ForwardContractView forward = registerForward("USD", "2000.00", "5.50");
    assertThat(forward.status()).isEqualTo(ForwardStatus.OPEN);

    // Settle at 5.70: result = (5.70 − 5.50) × 2000 = +400.00 BRL (the hedge paid off).
    ForwardContractView settled =
        restTemplate
            .postForEntity(
                "/api/exchange/forwards/" + forward.id() + "/settle",
                new java.util.HashMap<>(java.util.Map.of("effectiveRate", "5.70")),
                ForwardContractView.class)
            .getBody();

    assertThat(settled).isNotNull();
    assertThat(settled.status()).isEqualTo(ForwardStatus.SETTLED);
    assertThat(settled.settlementResultBrl()).isEqualByComparingTo(new BigDecimal("400.00"));

    // Settling again is a conflict (409).
    ResponseEntity<ApiErrorResponse> again =
        restTemplate.postForEntity(
            "/api/exchange/forwards/" + forward.id() + "/settle",
            new java.util.HashMap<>(java.util.Map.of("effectiveRate", "5.80")),
            ApiErrorResponse.class);
    assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(again.getBody()).isNotNull();
    assertThat(again.getBody().code()).isEqualTo("exchange.forward.not-open");
  }

  @Test
  void cancellingStopsTheCoverageAndRejectsFurtherTransitions() {
    ForwardContractView forward = registerForward("USD", "1000.00", "5.50");

    ForwardContractView cancelled =
        restTemplate
            .postForEntity(
                "/api/exchange/forwards/" + forward.id() + "/cancel",
                null,
                ForwardContractView.class)
            .getBody();
    assertThat(cancelled).isNotNull();
    assertThat(cancelled.status()).isEqualTo(ForwardStatus.CANCELLED);

    ResponseEntity<ApiErrorResponse> settleAfterCancel =
        restTemplate.postForEntity(
            "/api/exchange/forwards/" + forward.id() + "/settle",
            new java.util.HashMap<>(java.util.Map.of("effectiveRate", "5.70")),
            ApiErrorResponse.class);
    assertThat(settleAfterCancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void rejectsAnInvalidForward() {
    // Maturity before the trade date.
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/exchange/forwards",
            new RegisterForwardRequest(
                "USD",
                new BigDecimal("1000.00"),
                new BigDecimal("5.50"),
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 1),
                "Banco Alfa"),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("exchange.forward.invalid");
  }

  @Test
  void anOpenForwardHedgesTheBookAndSilencesTheDriftAlert() {
    // Same fixture as the exposure test: 2 open positions of USD 1000 (base 11100 BRL at freeze),
    // drift 500 > threshold 222 → alert ON on the uncovered book.
    AccountResponse account = createAccount();
    pinSellRate();
    recordMarketRate("5.55");
    confirmSale(account.id());
    confirmSale(account.id());
    recordMarketRate("5.80");

    LiveExposureView uncovered =
        restTemplate.getForEntity("/api/exchange/exposure", LiveExposureView.class).getBody();
    assertThat(uncovered).isNotNull();
    assertThat(uncovered.driftAlert()).isTrue();
    assertThat(uncovered.openForwards()).isZero();
    assertThat(uncovered.unhedgedExposureBase())
        .isEqualTo(Money.of(new BigDecimal("11100.00"), "BRL"));

    // Hedge the full USD 2000 exposure with a forward → unhedged base 0 → alert OFF.
    registerForward("USD", "2000.00", "5.60");

    LiveExposureView hedged =
        restTemplate.getForEntity("/api/exchange/exposure", LiveExposureView.class).getBody();
    assertThat(hedged).isNotNull();
    assertThat(hedged.openForwards()).isEqualTo(1);
    assertThat(hedged.unhedgedExposureBase()).isEqualTo(Money.of(new BigDecimal("0.00"), "BRL"));
    assertThat(hedged.driftAlert()).isFalse();
    // The exposure numbers themselves are unchanged — only the alert base is covered.
    assertThat(hedged.markToMarketDrift()).isEqualTo(uncovered.markToMarketDrift());
  }

  @Test
  void aPartialHedgeScalesTheThresholdToTheUncoveredShare() {
    AccountResponse account = createAccount();
    pinSellRate();
    recordMarketRate("5.55");
    confirmSale(account.id());
    confirmSale(account.id());
    recordMarketRate("5.80");

    // Hedge half (USD 1000 of 2000): unhedged base 5550 → threshold 111; drift 500 still alerts.
    registerForward("USD", "1000.00", "5.60");

    LiveExposureView exposure =
        restTemplate.getForEntity("/api/exchange/exposure", LiveExposureView.class).getBody();
    assertThat(exposure).isNotNull();
    assertThat(exposure.unhedgedExposureBase())
        .isEqualTo(Money.of(new BigDecimal("5550.00"), "BRL"));
    assertThat(exposure.driftThreshold()).isEqualTo(Money.of(new BigDecimal("111.00"), "BRL"));
    assertThat(exposure.driftAlert()).isTrue();
  }

  // --- helpers (fixture mirrors ExchangeExposureIntegrationTest) ---

  private ForwardContractView registerForward(String currency, String notional, String rate) {
    ForwardContractView view =
        restTemplate
            .postForEntity(
                "/api/exchange/forwards",
                new RegisterForwardRequest(
                    currency,
                    new BigDecimal(notional),
                    new BigDecimal(rate),
                    LocalDate.of(2026, 7, 1),
                    LocalDate.of(2026, 9, 1),
                    "Banco Alfa"),
                ForwardContractView.class)
            .getBody();
    assertThat(view).isNotNull();
    return view;
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

  private void recordMarketRate(String rate) {
    restTemplate.postForEntity(
        "/api/exchange/market-rates",
        new RecordMarketRateRequest("USD/BRL", new BigDecimal(rate), null),
        Void.class);
  }

  private BookingView confirmSale(UUID accountId) {
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
    BookingView booking =
        restTemplate
            .postForEntity(
                "/api/bookings",
                new CreateBookingRequest(
                    quote.id(),
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
}
