package com.fksoft.platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.platform.AuditType;
import com.fksoft.domain.platform.CertificateCustodyService;
import com.fksoft.domain.platform.ImportCertificateCommand;
import com.fksoft.domain.platform.JobOutcome;
import com.fksoft.domain.platform.PlatformJobService;
import com.fksoft.domain.platform.SystemAuditEntry;
import com.fksoft.domain.platform.SystemAuditService;
import com.fksoft.domain.platform.SystemAuditView;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the consolidated system audit (SPEC-0023 BR4; DL-0077). They prove the
 * audit consolidates job and certificate events with actor/correlation, filters by type, the entry
 * is append-only (no mutator), and the SECURITY regression: a custodied certificate's secret
 * material never appears in any audit row.
 */
class SystemAuditIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String SECRET = "PRIVATE-KEY-NEVER-IN-AUDIT-MATERIAL";

  @Autowired private SystemAuditService auditService;
  @Autowired private PlatformJobService jobService;
  @Autowired private CertificateCustodyService custodyService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM system_audit");
    jdbcTemplate.execute("DELETE FROM job_runs");
    jdbcTemplate.execute("DELETE FROM platform_certificates");
  }

  @Test
  void aGovernedJobRunIsConsolidatedIntoTheAudit() {
    jobService.runWithGovernance("aftersales-sla-sweep", "2026-06-30", () -> JobOutcome.of(2));

    var started =
        auditService.search(null, AuditType.JOB_RUN_STARTED, null, null, PageRequest.of(0, 20));
    var finished =
        auditService.search(null, AuditType.JOB_RUN_FINISHED, null, null, PageRequest.of(0, 20));

    assertThat(started.getContent()).isNotEmpty();
    assertThat(finished.getContent()).isNotEmpty();
    assertThat(finished.getContent().get(0).detail()).contains("SUCCEEDED");
  }

  @Test
  void custodyingACertificateIsAuditedWithoutLeakingTheMaterial() {
    custodyService.importCertificate(
        new ImportCertificateCommand(
            "CN=ACME:11222333000181",
            "11222333000181",
            LocalDate.now().minusDays(1),
            LocalDate.now().plusYears(1),
            SECRET.getBytes(StandardCharsets.UTF_8)),
        "ti-admin");

    var custodied =
        auditService.search(
            "ti-admin", AuditType.CERTIFICATE_CUSTODIED, null, null, PageRequest.of(0, 20));
    assertThat(custodied.getContent()).hasSize(1);
    SystemAuditView entry = custodied.getContent().get(0);
    assertThat(entry.actor()).isEqualTo("ti-admin");
    assertThat(entry.detail()).doesNotContain(SECRET); // BR1: metadata only
    assertThat(entry.detail()).contains("****0181"); // masked holder, not the full CNPJ

    // SECURITY regression: scan the whole audit table — the secret is nowhere.
    String allAudit = jdbcTemplate.queryForList("SELECT detail_json FROM system_audit").toString();
    assertThat(allAudit).doesNotContain(SECRET);
  }

  @Test
  void theAuditEntryIsAppendOnly() {
    // BR4: the entity exposes no mutator/setter — it can only be created and read, never rewritten.
    boolean hasMutator = false;
    for (Method method : SystemAuditEntry.class.getDeclaredMethods()) {
      String name = method.getName();
      if ((name.startsWith("set") || name.equals("anonymize") || name.startsWith("update"))
          && method.getParameterCount() > 0) {
        hasMutator = true;
      }
    }
    assertThat(hasMutator).as("system_audit is append-only — no mutators").isFalse();
  }

  @Test
  void theAuditIsFilterableByActorAndType() {
    auditService.record(AuditType.SECURITY_EVENT, "alice", "{\"k\":1}");
    auditService.record(AuditType.INTEGRATION_EVENT, "bob", "{\"k\":2}");

    var aliceSecurity =
        auditService.search("alice", AuditType.SECURITY_EVENT, null, null, PageRequest.of(0, 20));
    assertThat(aliceSecurity.getContent()).hasSize(1);
    assertThat(aliceSecurity.getContent().get(0).actor()).isEqualTo("alice");
  }
}
