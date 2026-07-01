package com.fksoft.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.admin.AdminContractExpiring;
import com.fksoft.domain.admin.AdminService;
import com.fksoft.domain.admin.AdminSupplierView;
import com.fksoft.domain.admin.RegisterContractCommand;
import com.fksoft.domain.admin.RegisterSupplierCommand;
import com.fksoft.infra.jobs.AdminContractExpiryScheduler;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Integration tests for slice 8l-3 (SPEC-0025 BR5; DL-0087): the controlled-clock contract-expiry
 * alert publishes {@code AdminContractExpiring} once per due contract (idempotent), ignores
 * contracts far from expiry, and the {@link AdminContractExpiryScheduler} adapter is wired into the
 * Platform job governance. Runs against a real Postgres (Testcontainers).
 */
@RecordApplicationEvents
class AdminContractExpiryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private AdminService adminService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApplicationEvents applicationEvents;
  @Autowired private AdminContractExpiryScheduler scheduler;

  private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");
  private static final LocalDate TODAY = NOW.atZone(ZoneOffset.UTC).toLocalDate();

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM admin_contracts");
    jdbcTemplate.execute("DELETE FROM admin_suppliers");
    jdbcTemplate.execute("DELETE FROM system_audit");
  }

  private AdminSupplierView supplier() {
    return adminService.registerSupplier(
        new RegisterSupplierCommand("SOFTWARE", null, "Sistema XPTO"), "admin");
  }

  private void contract(AdminSupplierView supplier, LocalDate validUntil) {
    adminService.registerContract(
        supplier.id(),
        new RegisterContractCommand(LocalDate.of(2026, 1, 1), validUntil, null, null, null),
        "admin");
  }

  @Test
  void theSchedulerAdapterIsWired() {
    assertThat(scheduler).isNotNull();
  }

  @Test
  void sweepPublishesAdminContractExpiringOnceForADueContract() {
    AdminSupplierView supplier = supplier();
    contract(supplier, TODAY.plusDays(10)); // within the 30-day window

    int flagged = adminService.flagExpiringContracts(NOW);
    assertThat(flagged).isEqualTo(1);
    assertThat(applicationEvents.stream(AdminContractExpiring.class).count()).isEqualTo(1);

    // Idempotent: a second sweep does not re-publish.
    assertThat(adminService.flagExpiringContracts(NOW.plusSeconds(60))).isZero();
  }

  @Test
  void sweepIgnoresContractsFarFromExpiryAndOpenEnded() {
    AdminSupplierView supplier = supplier();
    contract(supplier, TODAY.plusDays(90)); // far from expiry
    contract(supplier, null); // open-ended — never expires

    assertThat(adminService.flagExpiringContracts(NOW)).isZero();
  }
}
