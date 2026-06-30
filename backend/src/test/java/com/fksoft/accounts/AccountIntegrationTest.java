package com.fksoft.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.domain.accounts.AccountStatus;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
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
 * End-to-end tests for the accounts API (SPEC-0002 acceptance criteria) against a real Postgres
 * (Testcontainers): create returns 201 ACTIVE, duplicate document 409, invalid check digit 400,
 * fetch 200/404, and listing filters by status and paginates.
 */
class AccountIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String VALID_CNPJ = "12345678000195";

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM accounts");
  }

  @Test
  void createsValidCnpjAccountAsActive() {
    ResponseEntity<AccountResponse> response =
        restTemplate.postForEntity("/api/accounts", agency(VALID_CNPJ), AccountResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    AccountResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.id()).isNotNull();
    assertThat(body.legalType()).isEqualTo(LegalType.CNPJ);
    assertThat(body.documentNumber()).isEqualTo(VALID_CNPJ);
    assertThat(body.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(body.createdAt()).isNotNull();
  }

  @Test
  void rejectsDuplicateDocumentWith409() {
    restTemplate.postForEntity("/api/accounts", agency(VALID_CNPJ), AccountResponse.class);

    ResponseEntity<ApiErrorResponse> conflict =
        restTemplate.postForEntity("/api/accounts", agency(VALID_CNPJ), ApiErrorResponse.class);

    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody()).isNotNull();
    assertThat(conflict.getBody().code()).isEqualTo("account.document.duplicate");
  }

  @Test
  void rejectsInvalidCheckDigitWith400PointingToDocumentNumber() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/accounts", agency("12345678000196"), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("account.document.invalid");
    assertThat(response.getBody().fields())
        .extracting(ApiErrorResponse.FieldViolation::field)
        .contains("documentNumber");
  }

  @Test
  void fetchesByIdAndReturns404ForUnknownId() {
    AccountResponse created =
        restTemplate
            .postForEntity("/api/accounts", agency(VALID_CNPJ), AccountResponse.class)
            .getBody();
    assertThat(created).isNotNull();

    ResponseEntity<AccountResponse> found =
        restTemplate.getForEntity("/api/accounts/" + created.id(), AccountResponse.class);
    assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(found.getBody()).isNotNull();
    assertThat(found.getBody().id()).isEqualTo(created.id());

    ResponseEntity<ApiErrorResponse> missing =
        restTemplate.getForEntity("/api/accounts/" + UUID.randomUUID(), ApiErrorResponse.class);
    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(missing.getBody()).isNotNull();
    assertThat(missing.getBody().code()).isEqualTo("account.not-found");
  }

  @Test
  void listsFilteringByStatusWithPaginationEnvelope() {
    restTemplate.postForEntity("/api/accounts", agency(VALID_CNPJ), AccountResponse.class);

    ResponseEntity<JsonNode> active =
        restTemplate.getForEntity("/api/accounts?status=ACTIVE", JsonNode.class);
    assertThat(active.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode page = active.getBody();
    assertThat(page).isNotNull();
    assertThat(page.get("content")).hasSize(1);
    assertThat(page.get("totalElements").asInt()).isEqualTo(1);
    assertThat(page.get("page").asInt()).isZero();

    ResponseEntity<JsonNode> inactive =
        restTemplate.getForEntity("/api/accounts?status=INACTIVE", JsonNode.class);
    assertThat(inactive.getBody()).isNotNull();
    assertThat(inactive.getBody().get("content")).isEmpty();
  }

  @Test
  void rejectsInvalidStatusFilterWith400() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.getForEntity("/api/accounts?status=XPTO", ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("request.parameter-invalid");
  }

  private static CreateAccountRequest agency(String document) {
    return new CreateAccountRequest(
        LegalType.CNPJ, document, "Agência Sol e Mar", "26.123456.10.0001-9", null);
  }
}
