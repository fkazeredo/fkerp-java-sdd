package com.fksoft.cadastro;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.CreateCadastroItemRequest;
import com.fksoft.application.api.dto.UpdateCadastroItemRequest;
import com.fksoft.domain.cadastro.CadastroItemView;
import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.security.TestJwtTokens;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * REST API journey for the Cadastro module (SPEC-0031; ADR-0019/DL-0115): the seeded reference data
 * lists by type; a POLICY_ADMIN creates/updates/deactivates an item; a caller without the role is
 * forbidden (403); the write endpoints are gated. Runs against a real Postgres (Testcontainers) so
 * the V33 seed is present.
 */
class CadastroApiIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    // Remove only rows created by the tests (the seed uses created_by='system').
    jdbcTemplate.execute("DELETE FROM cadastro_item WHERE created_by <> 'system'");
    // Restore any item the tests may have deactivated back to active.
    jdbcTemplate.execute("UPDATE cadastro_item SET active = true WHERE created_by = 'system'");
  }

  @Test
  void listsTheConvertibleTypes() {
    ResponseEntity<List<CadastroType>> types =
        restTemplate.exchange(
            "/api/cadastro/types",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<CadastroType>>() {});
    assertThat(types.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(types.getBody()).contains(CadastroType.ASSET_TYPE, CadastroType.TAX_REGIME);
  }

  @Test
  void listsSeededItemsOfATypeActiveFirst() {
    List<CadastroItemView> items = itemsOf(CadastroType.ADMIN_EXPENSE_KIND);
    assertThat(items)
        .extracting(CadastroItemView::code)
        .contains("UTILITY", "AUTONOMOUS_SERVICE", "SERVICE", "OTHER");
    assertThat(items).allMatch(CadastroItemView::active);
  }

  @Test
  void policyAdminCanCreateUpdateAndDeactivateAnItem() {
    String admin = TestJwtTokens.mint("policy-admin", "ROLE_POLICY_ADMIN");

    // Create a brand-new code (pure reference data — DL-0115 seam).
    ResponseEntity<CadastroItemView> created =
        restTemplate.exchange(
            "/api/cadastro/items",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateCadastroItemRequest(CadastroType.ASSET_TYPE, "VEHICLE", "Veículo", 40),
                bearer(admin)),
            CadastroItemView.class);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    assertThat(created.getBody().code()).isEqualTo("VEHICLE");
    assertThat(created.getBody().active()).isTrue();

    // Update the label/order.
    ResponseEntity<CadastroItemView> updated =
        restTemplate.exchange(
            "/api/cadastro/items/" + created.getBody().id(),
            HttpMethod.PUT,
            new HttpEntity<>(
                new UpdateCadastroItemRequest("Veículo da frota", true, 41), bearer(admin)),
            CadastroItemView.class);
    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody()).isNotNull();
    assertThat(updated.getBody().label()).isEqualTo("Veículo da frota");

    // Deactivate (soft delete).
    ResponseEntity<CadastroItemView> deactivated =
        restTemplate.exchange(
            "/api/cadastro/items/" + created.getBody().id(),
            HttpMethod.DELETE,
            new HttpEntity<>(bearer(admin)),
            CadastroItemView.class);
    assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(deactivated.getBody()).isNotNull();
    assertThat(deactivated.getBody().active()).isFalse();
  }

  @Test
  void creatingAnItemWithoutThePolicyAdminRoleIsForbidden() {
    String other = TestJwtTokens.mint("someone", "ROLE_FINANCE");

    ResponseEntity<ApiErrorResponse> denied =
        restTemplate.exchange(
            "/api/cadastro/items",
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateCadastroItemRequest(CadastroType.ASSET_TYPE, "SHOULD_FAIL", "X", 0),
                bearer(other)),
            ApiErrorResponse.class);
    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void duplicateCodeForATypeIsRejected() {
    String admin = TestJwtTokens.mint("policy-admin", "ROLE_POLICY_ADMIN");
    ResponseEntity<ApiErrorResponse> dup =
        restTemplate.exchange(
            "/api/cadastro/items",
            HttpMethod.POST,
            new HttpEntity<>(
                // EQUIPMENT already exists for ASSET_TYPE (seeded).
                new CreateCadastroItemRequest(CadastroType.ASSET_TYPE, "EQUIPMENT", "Dup", 0),
                bearer(admin)),
            ApiErrorResponse.class);
    assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  private List<CadastroItemView> itemsOf(CadastroType type) {
    ResponseEntity<List<CadastroItemView>> response =
        restTemplate.exchange(
            "/api/cadastro/items?type=" + type.name(),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<CadastroItemView>>() {});
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private static HttpHeaders bearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
