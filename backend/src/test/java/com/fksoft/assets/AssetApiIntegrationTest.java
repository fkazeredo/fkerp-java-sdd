package com.fksoft.assets;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.RegisterAssetRequest;
import com.fksoft.application.api.dto.RetireAssetRequest;
import com.fksoft.domain.assets.AssetStatus;
import com.fksoft.domain.assets.AssetView;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * REST API journey for the Assets module slice 8h-1 (SPEC-0021 BR1/BR2/BR4/BR5; V26): register a
 * license linked to a Compliance document and a Finance entry by value (201, ACTIVE), read it,
 * retire it (audited, 200, RETIRED), list/filter by type and status, and the sad paths — a license
 * without expiresAt → 400, an unknown asset → 404, retiring twice → 409. Runs against a real
 * Postgres (Testcontainers).
 */
class AssetApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  private static final Money COST = Money.of(new BigDecimal("3200.00"), "BRL");

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM assets");
  }

  @Test
  void registerReadRetireAndListJourney() {
    UUID documentId = UUID.randomUUID();
    UUID financeEntryId = UUID.randomUUID();

    // 1) Register a software license linked to Compliance + Finance by value → 201 ACTIVE.
    ResponseEntity<AssetView> created =
        restTemplate.postForEntity(
            "/api/assets",
            new RegisterAssetRequest(
                "SOFTWARE_LICENSE",
                "JetBrains All Products Pack",
                java.time.LocalDate.of(2026, 1, 10),
                COST,
                java.time.LocalDate.of(2027, 1, 10),
                "JetBrains",
                documentId,
                financeEntryId),
            AssetView.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    assertThat(created.getBody().status()).isEqualTo(AssetStatus.ACTIVE);
    assertThat(created.getBody().documentId()).isEqualTo(documentId);
    assertThat(created.getBody().financeEntryId()).isEqualTo(financeEntryId);
    assertThat(created.getBody().acquisitionCost()).isEqualTo(COST);
    UUID assetId = created.getBody().id();

    // 2) Get → 200.
    ResponseEntity<AssetView> fetched =
        restTemplate.getForEntity("/api/assets/" + assetId, AssetView.class);
    assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(fetched.getBody()).isNotNull();
    assertThat(fetched.getBody().identifier()).isEqualTo("JetBrains All Products Pack");

    // 3) Retire → 200 RETIRED with audit.
    ResponseEntity<AssetView> retired =
        restTemplate.postForEntity(
            "/api/assets/" + assetId + "/retire",
            new RetireAssetRequest("Licença descontinuada"),
            AssetView.class);
    assertThat(retired.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(retired.getBody()).isNotNull();
    assertThat(retired.getBody().status()).isEqualTo(AssetStatus.RETIRED);
    assertThat(retired.getBody().retirementReason()).isEqualTo("Licença descontinuada");
    assertThat(retired.getBody().retiredBy()).isNotBlank();

    // 4) List filtered by status RETIRED contains it; ACTIVE does not.
    assertThat(list("/api/assets?status=RETIRED")).extracting(AssetView::id).contains(assetId);
    assertThat(list("/api/assets?status=ACTIVE")).extracting(AssetView::id).doesNotContain(assetId);
    // Filter by type combines.
    assertThat(list("/api/assets?type=SOFTWARE_LICENSE&status=RETIRED"))
        .extracting(AssetView::id)
        .containsExactly(assetId);
  }

  @Test
  void aSoftwareLicenseWithoutExpiresAtIs400() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/assets",
            new RegisterAssetRequest(
                "SOFTWARE_LICENSE",
                "Sem vencimento",
                java.time.LocalDate.of(2026, 1, 10),
                COST,
                null, // missing expiresAt
                null,
                null,
                null),
            ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("assets.license.expiry-required");
  }

  @Test
  void unknownAssetIs404() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity("/api/assets/" + UUID.randomUUID(), ApiErrorResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("assets.asset.not-found");
  }

  @Test
  void retiringTwiceIs409() {
    ResponseEntity<AssetView> created =
        restTemplate.postForEntity(
            "/api/assets",
            new RegisterAssetRequest(
                "EQUIPMENT",
                "Notebook Dell",
                java.time.LocalDate.of(2026, 1, 10),
                COST,
                null,
                null,
                null,
                null),
            AssetView.class);
    UUID assetId = created.getBody().id();
    restTemplate.postForEntity(
        "/api/assets/" + assetId + "/retire", new RetireAssetRequest("baixa"), AssetView.class);

    ResponseEntity<ApiErrorResponse> second =
        restTemplate.postForEntity(
            "/api/assets/" + assetId + "/retire",
            new RetireAssetRequest("de novo"),
            ApiErrorResponse.class);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(second.getBody()).isNotNull();
    assertThat(second.getBody().code()).isEqualTo("assets.asset.already-retired");
  }

  private java.util.List<AssetView> list(String url) {
    ResponseEntity<java.util.List<AssetView>> response =
        restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<java.util.List<AssetView>>() {});
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }
}
