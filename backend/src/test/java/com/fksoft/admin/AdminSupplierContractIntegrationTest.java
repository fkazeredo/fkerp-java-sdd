package com.fksoft.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.application.api.dto.LoginRequest;
import com.fksoft.application.api.dto.LoginResponse;
import com.fksoft.domain.admin.AdminContractInvalidException;
import com.fksoft.domain.admin.AdminContractRegistered;
import com.fksoft.domain.admin.AdminContractView;
import com.fksoft.domain.admin.AdminRecurrence;
import com.fksoft.domain.admin.AdminService;
import com.fksoft.domain.admin.AdminSupplierNotFoundException;
import com.fksoft.domain.admin.AdminSupplierRegistered;
import com.fksoft.domain.admin.AdminSupplierStatus;
import com.fksoft.domain.admin.AdminSupplierType;
import com.fksoft.domain.admin.AdminSupplierView;
import com.fksoft.domain.admin.RegisterContractCommand;
import com.fksoft.domain.admin.RegisterSupplierCommand;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Integration tests for slice 8l-1 (SPEC-0025 BR1/BR2/BR6; V30; DL-0084/0087/0088): registering an
 * administrative supplier (starts ACTIVE, publishes the event, audits the change), fetching/listing
 * with combinable filters, registering a contract that references a Compliance document by value
 * (invalid validity window → 400), and the role gate — a write without {@code ROLE_FINANCE} is
 * forbidden (403, audited), with it passes. Runs against a real Postgres (Testcontainers).
 */
@RecordApplicationEvents
class AdminSupplierContractIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AdminService adminService;
  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents applicationEvents;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM admin_expenses");
    jdbcTemplate.execute("DELETE FROM admin_contracts");
    jdbcTemplate.execute("DELETE FROM admin_suppliers");
    jdbcTemplate.execute("DELETE FROM system_audit");
  }

  private AdminSupplierView energySupplier() {
    return adminService.registerSupplier(
        new RegisterSupplierCommand(
            AdminSupplierType.UTILITY, "61695227000193", "Companhia de Energia"),
        "admin");
  }

  @Test
  void registeringASupplierStartsItActivePublishesTheEventAndAudits() {
    AdminSupplierView supplier = energySupplier();

    assertThat(supplier.status()).isEqualTo(AdminSupplierStatus.ACTIVE);
    assertThat(supplier.type()).isEqualTo(AdminSupplierType.UTILITY);
    assertThat(adminService.getSupplier(supplier.id()).displayName())
        .isEqualTo("Companhia de Energia");
    assertThat(
            applicationEvents.stream(AdminSupplierRegistered.class)
                .filter(e -> e.supplierRef().equals(supplier.id().toString()))
                .count())
        .isEqualTo(1);

    // BR6/DL-0088: the change is audited in system_audit as ADMIN_CHANGE — never the full CNPJ.
    Integer audits =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM system_audit WHERE type = 'ADMIN_CHANGE' AND actor = 'admin'",
            Integer.class);
    assertThat(audits).isEqualTo(1);
    String detail =
        jdbcTemplate.queryForObject(
            "SELECT detail_json::text FROM system_audit WHERE type='ADMIN_CHANGE' LIMIT 1",
            String.class);
    assertThat(detail).contains("SUPPLIER_REGISTERED").doesNotContain("61695227000193");
  }

  @Test
  void fetchingAMissingSupplierIsNotFound() {
    assertThatThrownBy(() -> adminService.getSupplier(UUID.randomUUID()))
        .isInstanceOf(AdminSupplierNotFoundException.class);
  }

  @Test
  void listingFiltersByTypeAndStatus() {
    AdminSupplierView utility = energySupplier();
    adminService.registerSupplier(
        new RegisterSupplierCommand(AdminSupplierType.SOFTWARE, null, "Sistema XPTO"), "admin");

    assertThat(adminService.listSuppliers(AdminSupplierType.UTILITY, null))
        .extracting(AdminSupplierView::id)
        .containsExactly(utility.id());
    assertThat(adminService.listSuppliers(null, AdminSupplierStatus.ACTIVE)).hasSize(2);
    assertThat(adminService.listSuppliers(AdminSupplierType.SOFTWARE, AdminSupplierStatus.INACTIVE))
        .isEmpty();
  }

  @Test
  void registeringAContractLinksTheComplianceDocumentByValueAndPublishesTheEvent() {
    AdminSupplierView supplier = energySupplier();
    UUID documentId = UUID.randomUUID();
    AdminContractView contract =
        adminService.registerContract(
            supplier.id(),
            new RegisterContractCommand(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                AdminRecurrence.MONTHLY,
                Money.of(new BigDecimal("840.00"), "BRL"),
                documentId),
            "admin");

    assertThat(contract.documentId()).isEqualTo(documentId);
    assertThat(contract.recurrence()).isEqualTo(AdminRecurrence.MONTHLY);
    assertThat(contract.amount()).isEqualTo(Money.of(new BigDecimal("840.00"), "BRL"));
    assertThat(adminService.contractsForSupplier(supplier.id())).hasSize(1);
    assertThat(
            applicationEvents.stream(AdminContractRegistered.class)
                .filter(e -> e.supplierRef().equals(supplier.id().toString()))
                .count())
        .isEqualTo(1);
  }

  @Test
  void registeringAContractForAMissingSupplierIsNotFound() {
    assertThatThrownBy(
            () ->
                adminService.registerContract(
                    UUID.randomUUID(),
                    new RegisterContractCommand(LocalDate.of(2026, 1, 1), null, null, null, null),
                    "admin"))
        .isInstanceOf(AdminSupplierNotFoundException.class);
  }

  @Test
  void contractWithInvalidValidityWindowIsRejected() {
    AdminSupplierView supplier = energySupplier();
    assertThatThrownBy(
            () ->
                adminService.registerContract(
                    supplier.id(),
                    new RegisterContractCommand(
                        LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1), null, null, null),
                    "admin"))
        .isInstanceOf(AdminContractInvalidException.class);
  }

  // --- Security gate (DL-0088): write needs ROLE_FINANCE ---

  @Test
  void registeringASupplierWithoutFinanceRoleIsForbiddenAndAudited() {
    // 'director' has ROLE_DIRECTOR but NOT ROLE_FINANCE — the administrative write must be denied.
    String token = login("director");

    ResponseEntity<ApiErrorResponse> denied =
        restTemplate.exchange(
            "/api/admin/suppliers",
            HttpMethod.POST,
            new HttpEntity<>(supplierBody(), jsonBearer(token)),
            ApiErrorResponse.class);

    assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(denied.getBody()).isNotNull();
    assertThat(denied.getBody().code()).isEqualTo("access.denied");

    Integer denials =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM system_audit WHERE type = 'ACCESS_DENIED' AND actor = 'director'",
            Integer.class);
    assertThat(denials).isEqualTo(1);
  }

  @Test
  void registeringASupplierWithFinanceRoleSucceeds() {
    String token = login("finance");

    ResponseEntity<AdminSupplierView> created =
        restTemplate.exchange(
            "/api/admin/suppliers",
            HttpMethod.POST,
            new HttpEntity<>(supplierBody(), jsonBearer(token)),
            AdminSupplierView.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).isNotNull();
    assertThat(created.getBody().status()).isEqualTo(AdminSupplierStatus.ACTIVE);
  }

  private static String supplierBody() {
    return "{\"type\":\"UTILITY\",\"identifier\":\"61695227000193\","
        + "\"displayName\":\"Companhia de Energia\"}";
  }

  private String login(String username) {
    LoginResponse body =
        restTemplate
            .postForEntity(
                "/api/identity/login", new LoginRequest(username, "dev12345"), LoginResponse.class)
            .getBody();
    assertThat(body).isNotNull();
    return body.token();
  }

  private static HttpHeaders jsonBearer(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }
}
