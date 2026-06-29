package com.fksoft.commissioning;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.CommissionPreviewRequest;
import com.fksoft.domain.commissioning.CommissionStatement;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for the commission preview endpoint (SPEC-0004): the happy decomposition and the
 * out-of-range percentage error.
 */
class CommissioningIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void previewsTheTwoSidedDecomposition() {
    ResponseEntity<CommissionStatement> response =
        restTemplate.postForEntity(
            "/api/commissioning/preview",
            new CommissionPreviewRequest(
                Money.of(new BigDecimal("500.00"), "USD"),
                new BigDecimal("0.15"),
                new BigDecimal("0.10")),
            CommissionStatement.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    CommissionStatement body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.supplierCommission()).isEqualTo(Money.of(new BigDecimal("75.00"), "USD"));
    assertThat(body.agentCommission()).isEqualTo(Money.of(new BigDecimal("50.00"), "USD"));
    assertThat(body.spread()).isEqualTo(Money.of(new BigDecimal("25.00"), "USD"));
    assertThat(body.spreadNegative()).isFalse();
  }

  @Test
  void rejectsOutOfRangePercentageWith400() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/commissioning/preview",
            new CommissionPreviewRequest(
                Money.of(new BigDecimal("500.00"), "USD"),
                new BigDecimal("1.5"),
                new BigDecimal("0.10")),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("commissioning.pct.invalid");
  }
}
