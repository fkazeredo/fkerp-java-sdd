package com.fksoft.sourcing;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.RegisterSourcedOfferRequest;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.sourcing.IntegrationLevel;
import com.fksoft.domain.sourcing.OfferOrigin;
import com.fksoft.domain.sourcing.SourcedOfferView;
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
 * End-to-end tests for sourced offers (SPEC-0009) against real Postgres: manual registration of an
 * offer's provenance, fetch by id, the non-empty product text invariant (BR1), and 404 for an
 * unknown id.
 */
class SourcingIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM sourced_offers");
  }

  @Test
  void registersAndFetchesASourcedOffer() {
    ResponseEntity<SourcedOfferView> created =
        restTemplate.postForEntity(
            "/api/sourcing/offers",
            new RegisterSourcedOfferRequest(
                "City Tour Rio - full day",
                Money.of(new BigDecimal("480.00"), "BRL"),
                OfferOrigin.EXTERNAL_SITE,
                IntegrationLevel.INBOUND,
                "QS-2026-555"),
            SourcedOfferView.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    SourcedOfferView offer = created.getBody();
    assertThat(offer).isNotNull();
    assertThat(offer.id()).isNotNull();
    assertThat(offer.origin()).isEqualTo(OfferOrigin.EXTERNAL_SITE);
    assertThat(offer.integrationLevel()).isEqualTo(IntegrationLevel.INBOUND);

    ResponseEntity<SourcedOfferView> fetched =
        restTemplate.getForEntity("/api/sourcing/offers/" + offer.id(), SourcedOfferView.class);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody()).isNotNull();
    assertThat(fetched.getBody().productText()).isEqualTo("City Tour Rio - full day");
    assertThat(fetched.getBody().basePrice()).isEqualTo(Money.of(new BigDecimal("480.00"), "BRL"));
  }

  @Test
  void rejectsBlankProductTextWith400() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/sourcing/offers",
            new RegisterSourcedOfferRequest(
                "   ",
                Money.of(new BigDecimal("480.00"), "BRL"),
                OfferOrigin.EXTERNAL_SITE,
                IntegrationLevel.INBOUND,
                null),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
  }

  @Test
  void returns404ForUnknownOffer() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity(
            "/api/sourcing/offers/" + UUID.randomUUID(), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("sourcing.offer.not-found");
  }
}
