package com.fksoft.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fksoft.application.api.dto.MarketRateResponse;
import com.fksoft.application.api.dto.RecordMarketRateRequest;
import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.exchange.MarketRateProvider;
import com.fksoft.domain.exchange.MarketRateSource;
import com.fksoft.domain.exchange.MarketRateView;
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
 * End-to-end tests for the market-rate API and port (SPEC-0011, slice 1, BR1): manual contingency
 * registration creates an observation tagged MANUAL; "market now" is the most recent observation
 * not in the future, served via REST and via the {@link MarketRateProvider} port (consumed by the
 * FxPosition opening); unknown pair → 404; non-positive rate → 400; history is newest-first.
 */
class MarketRateIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MarketRateProvider marketRateProvider;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM market_rates");
  }

  @Test
  void recordsManualObservationAndServesMarketNow() {
    ResponseEntity<MarketRateResponse> recorded =
        restTemplate.postForEntity(
            "/api/exchange/market-rates",
            new RecordMarketRateRequest("USD/BRL", new BigDecimal("5.55"), null),
            MarketRateResponse.class);
    assertThat(recorded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(recorded.getBody()).isNotNull();
    assertThat(recorded.getBody().source()).isEqualTo(MarketRateSource.MANUAL);

    ResponseEntity<MarketRateResponse> current =
        restTemplate.getForEntity(
            "/api/exchange/market-rates/current?pair=USD-BRL", MarketRateResponse.class);
    assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(current.getBody()).isNotNull();
    assertThat(current.getBody().currencyPair()).isEqualTo("USD/BRL");
    assertThat(current.getBody().rate()).isEqualByComparingTo("5.55");
  }

  @Test
  void marketNowIsTheMostRecentObservationNotInTheFuture() {
    Instant base = Instant.parse("2026-06-20T12:00:00Z");
    restTemplate.postForEntity(
        "/api/exchange/market-rates",
        new RecordMarketRateRequest("USD/BRL", new BigDecimal("5.50"), base),
        MarketRateResponse.class);
    restTemplate.postForEntity(
        "/api/exchange/market-rates",
        new RecordMarketRateRequest(
            "USD/BRL", new BigDecimal("5.70"), Instant.now().plus(Duration.ofDays(1))),
        MarketRateResponse.class);

    // The future-dated observation is not "market now": the prevailing one is 5.50.
    Optional<MarketRateView> viaPort =
        marketRateProvider.marketRateAt(CurrencyPair.parse("USD/BRL"), Instant.now());
    assertThat(viaPort).isPresent();
    assertThat(viaPort.orElseThrow().rate()).isEqualByComparingTo("5.50");
    assertThat(viaPort.orElseThrow().source()).isEqualTo(MarketRateSource.MANUAL);
  }

  @Test
  void returns404WhenNoMarketRateForPair() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/exchange/market-rates/current?pair=EUR-BRL", ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("exchange.market-rate.not-found");
  }

  @Test
  void rejectsNonPositiveRateWith400() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/exchange/market-rates",
            new RecordMarketRateRequest("USD/BRL", new BigDecimal("0.00"), null),
            ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("exchange.rate.invalid");
  }

  @Test
  void listsHistoryNewestFirst() {
    Instant base = Instant.parse("2026-06-20T12:00:00Z");
    restTemplate.postForEntity(
        "/api/exchange/market-rates",
        new RecordMarketRateRequest("USD/BRL", new BigDecimal("5.50"), base),
        MarketRateResponse.class);
    restTemplate.postForEntity(
        "/api/exchange/market-rates",
        new RecordMarketRateRequest(
            "USD/BRL", new BigDecimal("5.70"), base.plus(Duration.ofDays(2))),
        MarketRateResponse.class);

    ResponseEntity<JsonNode> history =
        restTemplate.getForEntity("/api/exchange/market-rates?pair=USD-BRL", JsonNode.class);
    assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode page = history.getBody();
    assertThat(page).isNotNull();
    assertThat(page.get("totalElements").asInt()).isEqualTo(2);
    assertThat(new BigDecimal(page.get("content").get(0).get("rate").asText()))
        .isEqualByComparingTo("5.70");
    assertThat(new BigDecimal(page.get("content").get(1).get("rate").asText()))
        .isEqualByComparingTo("5.50");
  }
}
