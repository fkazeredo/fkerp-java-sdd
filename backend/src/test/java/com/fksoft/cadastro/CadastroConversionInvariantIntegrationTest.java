package com.fksoft.cadastro;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.RegisterAdminSupplierRequest;
import com.fksoft.application.api.dto.RegisterAssetRequest;
import com.fksoft.domain.admin.AdminSupplierView;
import com.fksoft.domain.assets.AssetView;
import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
import com.fksoft.domain.money.Money;
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
 * Proves the enum→cadastro invariant (SPEC-0031 BR4/AC3; ADR-0019/DL-0115): a converted field
 * round-trips the SAME wire value (the code = old enum name), and an unknown/inactive code is
 * rejected (422) by the {@link CadastroValidator}. Exercises the Admin and Assets writes end-to-end
 * against a real Postgres (Testcontainers) with the V33 seed present. The Billing branch (regime →
 * strategy) is covered by {@code TaxRegimeStrategyTest}/{@code CommissionInvoiceTest}.
 */
class CadastroConversionInvariantIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private CadastroValidator cadastroValidator;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM admin_suppliers");
    jdbcTemplate.execute("DELETE FROM assets");
    jdbcTemplate.execute("DELETE FROM system_audit");
    jdbcTemplate.execute("UPDATE cadastro_item SET active = true WHERE created_by = 'system'");
  }

  @Test
  void supplierTypeCodeRoundTripsTheSameWireValue() {
    ResponseEntity<AdminSupplierView> created =
        restTemplate.postForEntity(
            "/api/admin/suppliers",
            new RegisterAdminSupplierRequest("UTILITY", "61695227000193", "Companhia de Energia"),
            AdminSupplierView.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    // The wire value is unchanged — the code equals the old enum name.
    assertThat(created.getBody().type()).isEqualTo("UTILITY");
  }

  @Test
  void anUnknownSupplierTypeCodeIsRejected() {
    ResponseEntity<ApiErrorResponse> rejected =
        restTemplate.postForEntity(
            "/api/admin/suppliers",
            new RegisterAdminSupplierRequest("NOT_A_TYPE", null, "X"),
            ApiErrorResponse.class);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(rejected.getBody()).isNotNull();
    assertThat(rejected.getBody().code()).isEqualTo("cadastro.code.invalid");
  }

  @Test
  void anInactiveAssetTypeCodeIsRejected() {
    // Deactivate the OTHER asset-type code, then try to use it → rejected.
    jdbcTemplate.update(
        "UPDATE cadastro_item SET active = false WHERE type = 'ASSET_TYPE' AND code = 'OTHER'");

    ResponseEntity<ApiErrorResponse> rejected =
        restTemplate.postForEntity(
            "/api/assets",
            new RegisterAssetRequest(
                "OTHER",
                "Mesa",
                LocalDate.of(2026, 1, 1),
                Money.of(new BigDecimal("100.00"), "BRL"),
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID()),
            ApiErrorResponse.class);
    assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(rejected.getBody()).isNotNull();
    assertThat(rejected.getBody().code()).isEqualTo("cadastro.code.invalid");
    assertThat(cadastroValidator.isValid(CadastroType.ASSET_TYPE, "OTHER")).isFalse();
  }

  @Test
  void aValidActiveAssetTypeCodeRoundTrips() {
    ResponseEntity<AssetView> created =
        restTemplate.postForEntity(
            "/api/assets",
            new RegisterAssetRequest(
                "EQUIPMENT",
                "Notebook",
                LocalDate.of(2026, 1, 1),
                Money.of(new BigDecimal("3200.00"), "BRL"),
                null,
                null,
                null,
                null),
            AssetView.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    assertThat(created.getBody().type()).isEqualTo("EQUIPMENT");
  }
}
