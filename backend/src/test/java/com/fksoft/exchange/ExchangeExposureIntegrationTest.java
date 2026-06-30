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
import com.fksoft.domain.exchange.LiveExposureView;
import com.fksoft.domain.exchange.PromoFxResultView;
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
 * End-to-end tests for the FX exposure read-models (SPEC-0011 BR6/BR9, slice 10c): the aggregate
 * {@code LiveExposure} sums the open positions' subsidy + drift over <strong>multiple
 * positions</strong> and raises the drift alert when |drift| crosses 2% of the open foreign
 * exposure (DL-0027); the {@code PromoFxResult} for a period splits subsidy × drift × gap. The
 * market move is driven by recording a newer market observation (a controlled market fixture).
 */
class ExchangeExposureIntegrationTest extends AbstractPostgresIntegrationTest {

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
  void aggregatesOpenPositionsAndAlertsWhenDriftCrossesTwoPercent() {
    AccountResponse account = createAccount();
    pinSellRate();
    recordMarketRate("5.55"); // market-at-freeze for both positions
    confirmSale(account.id());
    confirmSale(account.id());

    // Two open positions: subsidy = 2 × (5.55 − 5.40) × 1000 = 300.00.
    // Open foreign exposure base = 2 × (1000 × 5.55) = 11100 → threshold 2% = 222.00.
    // Market moves to 5.80 → drift = 2 × (5.80 − 5.55) × 1000 = 500.00 > 222.00 → alert.
    recordMarketRate("5.80");

    LiveExposureView exposure =
        restTemplate.getForEntity("/api/exchange/exposure", LiveExposureView.class).getBody();

    assertThat(exposure).isNotNull();
    assertThat(exposure.openPositions()).isEqualTo(2);
    assertThat(exposure.accruedSubsidy()).isEqualTo(Money.of(new BigDecimal("300.00"), "BRL"));
    assertThat(exposure.markToMarketDrift()).isEqualTo(Money.of(new BigDecimal("500.00"), "BRL"));
    assertThat(exposure.totalExposure()).isEqualTo(Money.of(new BigDecimal("800.00"), "BRL"));
    assertThat(exposure.driftThreshold()).isEqualTo(Money.of(new BigDecimal("222.00"), "BRL"));
    assertThat(exposure.driftAlert()).isTrue();
  }

  @Test
  void doesNotAlertWhenDriftIsWithinThreshold() {
    AccountResponse account = createAccount();
    pinSellRate();
    recordMarketRate("5.55");
    confirmSale(account.id());
    confirmSale(account.id());

    // Market moves only to 5.60 → drift = 2 × 0.05 × 1000 = 100.00 < 222.00 → no alert.
    recordMarketRate("5.60");

    LiveExposureView exposure =
        restTemplate.getForEntity("/api/exchange/exposure", LiveExposureView.class).getBody();

    assertThat(exposure).isNotNull();
    assertThat(exposure.markToMarketDrift()).isEqualTo(Money.of(new BigDecimal("100.00"), "BRL"));
    assertThat(exposure.driftAlert()).isFalse();
  }

  @Test
  void emptyBookHasZeroExposureAndNoAlert() {
    LiveExposureView exposure =
        restTemplate.getForEntity("/api/exchange/exposure", LiveExposureView.class).getBody();

    assertThat(exposure).isNotNull();
    assertThat(exposure.openPositions()).isEqualTo(0);
    assertThat(exposure.totalExposure()).isEqualTo(Money.of(new BigDecimal("0.00"), "BRL"));
    assertThat(exposure.driftAlert()).isFalse();
  }

  @Test
  void promoFxSplitsSubsidyDriftAndGapForThePeriod() {
    AccountResponse account = createAccount();
    pinSellRate();
    recordMarketRate("5.55");
    BookingView booking = confirmSale(account.id());
    settleAt(firstOpenCaseId(), "5.70");

    // The single closed position (OVERVIEW 7.2): subsidy 150, realizedDrift 150, gap 300.
    String period = currentPeriod();
    PromoFxResultView promo =
        restTemplate
            .getForEntity(
                "/api/exchange/reports/promo-fx?period=" + period, PromoFxResultView.class)
            .getBody();

    assertThat(promo).isNotNull();
    assertThat(promo.positions()).isEqualTo(1);
    assertThat(promo.subsidy()).isEqualTo(Money.of(new BigDecimal("150.00"), "BRL"));
    assertThat(promo.drift()).isEqualTo(Money.of(new BigDecimal("150.00"), "BRL"));
    assertThat(promo.totalGap()).isEqualTo(Money.of(new BigDecimal("300.00"), "BRL"));
    assertThat(booking).isNotNull();
  }

  @Test
  void promoFxRejectsMalformedPeriodWith400() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/exchange/reports/promo-fx?period=2026-13-XX", ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("exchange.period.invalid");
  }

  private String currentPeriod() {
    JsonNode exposure =
        restTemplate.getForEntity("/api/exchange/exposure", JsonNode.class).getBody();
    assertThat(exposure).isNotNull();
    // asOf is an ISO instant; take YYYY-MM.
    return exposure.get("asOf").asText().substring(0, 7);
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

  private void settleAt(UUID caseId, String settlementRate) {
    restTemplate.postForEntity(
        "/api/reconciliation/" + caseId + "/settlement",
        new SettlementRequest(
            Money.of(new BigDecimal("6000.00"), "BRL"),
            new BigDecimal(settlementRate),
            Money.of(new BigDecimal("5700.00"), "BRL"),
            Money.of(new BigDecimal("810.00"), "BRL"),
            Money.of(new BigDecimal("540.00"), "BRL")),
        ReconciliationCaseView.class);
  }

  private UUID firstOpenCaseId() {
    JsonNode page = restTemplate.getForEntity("/api/reconciliation", JsonNode.class).getBody();
    assertThat(page).isNotNull();
    assertThat(page.get("content")).isNotEmpty();
    return UUID.fromString(page.get("content").get(0).get("caseId").asText());
  }
}
