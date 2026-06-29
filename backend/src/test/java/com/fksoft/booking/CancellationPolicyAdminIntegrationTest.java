package com.fksoft.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.CancellationPolicyRequest;
import com.fksoft.application.api.dto.CancellationPolicyRequest.WindowRequest;
import com.fksoft.domain.booking.CancellationPolicyView;
import com.fksoft.domain.booking.CancellationType;
import com.fksoft.domain.booking.CostBearer;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for administering the cancellation/no-show policy source per product/supplier
 * scope (SPEC-0010 API {@code GET/PUT /api/products/{ref}/cancellation-policy}): the safe default
 * for an unknown scope, the upsert round-trip (including merchant-of-record and no-show fee), and
 * the malformed-window rejection (400 {@code cancellation.policy.invalid}).
 */
class CancellationPolicyAdminIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM cancellation_policies");
  }

  @Test
  void returnsSafeDefaultForAnUnknownScope() {
    ResponseEntity<CancellationPolicyView> response =
        restTemplate.getForEntity(
            "/api/products/PORTAL-EXP-TOUR-42/cancellation-policy", CancellationPolicyView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    CancellationPolicyView view = response.getBody();
    assertThat(view).isNotNull();
    assertThat(view.type()).isEqualTo(CancellationType.STANDARD);
    assertThat(view.merchantOfRecord()).isFalse();
    assertThat(view.windows()).isEmpty();
    assertThat(view.noShowFee()).isNull();
  }

  @Test
  void upsertsAndReadsBackTheAdministeredPolicy() {
    CancellationPolicyRequest request =
        new CancellationPolicyRequest(
            CancellationType.STANDARD,
            List.of(
                new WindowRequest(24, new BigDecimal("0.50")),
                new WindowRequest(72, new BigDecimal("0.25"))),
            true,
            CostBearer.AGENCY,
            false,
            Money.of(new BigDecimal("90.00"), "BRL"),
            true);

    restTemplate.put("/api/products/CAR-ALAMO/cancellation-policy", request);

    CancellationPolicyView view =
        restTemplate
            .getForEntity(
                "/api/products/CAR-ALAMO/cancellation-policy", CancellationPolicyView.class)
            .getBody();

    assertThat(view).isNotNull();
    assertThat(view.type()).isEqualTo(CancellationType.STANDARD);
    assertThat(view.windows()).hasSize(2);
    assertThat(view.costBearer()).isEqualTo(CostBearer.AGENCY);
    assertThat(view.noShowFee()).isEqualTo(Money.of(new BigDecimal("90.00"), "BRL"));
    assertThat(view.waivedIfFlightCancelled()).isTrue();
  }

  @Test
  void storesTheMerchantOfRecordFlagForAllSalesFinal() {
    CancellationPolicyRequest request =
        new CancellationPolicyRequest(
            CancellationType.ALL_SALES_FINAL,
            List.of(),
            false,
            CostBearer.SUPPLIER,
            true,
            null,
            false);

    restTemplate.put("/api/products/PORTAL-EXP-TOUR-7/cancellation-policy", request);

    CancellationPolicyView view =
        restTemplate
            .getForEntity(
                "/api/products/PORTAL-EXP-TOUR-7/cancellation-policy", CancellationPolicyView.class)
            .getBody();

    assertThat(view).isNotNull();
    assertThat(view.type()).isEqualTo(CancellationType.ALL_SALES_FINAL);
    assertThat(view.merchantOfRecord()).isTrue();
    assertThat(view.refundable()).isFalse();
  }

  @Test
  void rejectsAMalformedWindow() {
    CancellationPolicyRequest request =
        new CancellationPolicyRequest(
            CancellationType.STANDARD,
            List.of(new WindowRequest(24, new BigDecimal("1.50"))),
            true,
            CostBearer.AGENCY,
            false,
            null,
            false);

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.exchange(
            "/api/products/BAD-POLICY/cancellation-policy",
            HttpMethod.PUT,
            new HttpEntity<>(request),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("cancellation.policy.invalid");
  }
}
