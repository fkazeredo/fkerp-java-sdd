package com.fksoft.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.application.api.dto.PinnedSellRateResponse;
import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.exchange.ExchangeRateProvider;
import com.fksoft.domain.exchange.PinnedSellRateView;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the exchange API and Open-Host port (SPEC-0003): pin + current, future-dated
 * rate not served before its time, current 404, invalid rate 400, paginated history newest-first,
 * and {@link ExchangeRateProvider#currentRate} returning the right value for Quoting.
 */
class ExchangeIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ExchangeRateProvider exchangeRateProvider;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM pinned_sell_rates");
  }

  @Test
  void pinsAndServesCurrentRate() {
    ResponseEntity<PinnedSellRateResponse> pinned =
        restTemplate.postForEntity(
            "/api/exchange/pinned-rates",
            new PinRateRequest("USD/BRL", new BigDecimal("5.40"), null, "promo Orlando"),
            PinnedSellRateResponse.class);
    assertThat(pinned.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    ResponseEntity<PinnedSellRateResponse> current =
        restTemplate.getForEntity(
            "/api/exchange/pinned-rates/current?pair=USD-BRL", PinnedSellRateResponse.class);
    assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(current.getBody()).isNotNull();
    assertThat(current.getBody().currencyPair()).isEqualTo("USD/BRL");
    assertThat(current.getBody().rate()).isEqualByComparingTo("5.40");
  }

  @Test
  void doesNotServeAFutureDatedRateBeforeItsTime() {
    restTemplate.postForEntity(
        "/api/exchange/pinned-rates",
        new PinRateRequest("USD/BRL", new BigDecimal("5.40"), null, null),
        PinnedSellRateResponse.class);
    restTemplate.postForEntity(
        "/api/exchange/pinned-rates",
        new PinRateRequest(
            "USD/BRL", new BigDecimal("5.55"), Instant.now().plus(Duration.ofDays(1)), "agendada"),
        PinnedSellRateResponse.class);

    ResponseEntity<PinnedSellRateResponse> current =
        restTemplate.getForEntity(
            "/api/exchange/pinned-rates/current?pair=USD-BRL", PinnedSellRateResponse.class);
    assertThat(current.getBody()).isNotNull();
    assertThat(current.getBody().rate()).isEqualByComparingTo("5.40");

    Optional<PinnedSellRateView> viaPort =
        exchangeRateProvider.currentRate(CurrencyPair.parse("USD/BRL"));
    assertThat(viaPort).isPresent();
    assertThat(viaPort.orElseThrow().rate()).isEqualByComparingTo("5.40");
  }

  @Test
  void returns404WhenNoRateForPair() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/exchange/pinned-rates/current?pair=EUR-BRL", ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("exchange.rate.not-found");
  }

  @Test
  void rejectsNonPositiveRateWith400() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/exchange/pinned-rates",
            new PinRateRequest("USD/BRL", new BigDecimal("0.00"), null, null),
            ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("exchange.rate.invalid");
  }

  @Test
  void listsHistoryNewestFirst() {
    Instant base = Instant.parse("2026-06-20T12:00:00Z");
    restTemplate.postForEntity(
        "/api/exchange/pinned-rates",
        new PinRateRequest("USD/BRL", new BigDecimal("5.30"), base, null),
        PinnedSellRateResponse.class);
    restTemplate.postForEntity(
        "/api/exchange/pinned-rates",
        new PinRateRequest("USD/BRL", new BigDecimal("5.40"), base.plus(Duration.ofDays(2)), null),
        PinnedSellRateResponse.class);

    ResponseEntity<JsonNode> history =
        restTemplate.getForEntity("/api/exchange/pinned-rates?pair=USD-BRL", JsonNode.class);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode page = history.getBody();
    assertThat(page).isNotNull();
    assertThat(page.get("totalElements").asInt()).isEqualTo(2);
    assertThat(new BigDecimal(page.get("content").get(0).get("rate").asText()))
        .isEqualByComparingTo("5.40");
    assertThat(new BigDecimal(page.get("content").get(1).get("rate").asText()))
        .isEqualByComparingTo("5.30");
  }
}
