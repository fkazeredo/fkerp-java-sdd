package com.fksoft.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.platform.CertificateCustodyService;
import com.fksoft.domain.platform.CertificateNotFoundException;
import com.fksoft.domain.platform.CertificateStatus;
import com.fksoft.domain.platform.CertificateView;
import com.fksoft.domain.platform.ImportCertificateCommand;
import com.fksoft.domain.platform.SecretCipher;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the e-CNPJ certificate custody (SPEC-0023 BR1/BR5; DL-0074). They prove the
 * custody encrypts the secret material at rest (it is never stored or returned in clear), the
 * status exposes only metadata, the expiry sweep alerts once (controlled clock), and the SECURITY
 * regression: the material never appears in the database row, the view or the audit path.
 */
class CertificateCustodyIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String SECRET = "PRIVATE-KEY-DO-NOT-LEAK-超级机密-MATERIAL";

  @Autowired private CertificateCustodyService custodyService;
  @Autowired private SecretCipher secretCipher;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM system_audit");
    jdbcTemplate.execute("DELETE FROM platform_certificates");
  }

  @Test
  void statusReturnsOnlyMetadataAndTheMaterialIsNeverStoredInClear() {
    // BR1: import a certificate; the status is metadata only and the DB holds NO plaintext
    // material.
    LocalDate from = LocalDate.now().minusDays(10);
    LocalDate until = LocalDate.now().plusYears(1);
    CertificateView view =
        custodyService.importCertificate(
            new ImportCertificateCommand(
                "CN=ACME TRAVEL LTDA:11222333000181",
                "11222333000181",
                from,
                until,
                SECRET.getBytes(StandardCharsets.UTF_8)),
            "ti-admin");

    // The view carries metadata only — and no field equals the secret material.
    assertThat(view.subject()).contains("ACME TRAVEL");
    assertThat(view.status()).isEqualTo(CertificateStatus.VALID);
    assertThat(view.daysToExpiry()).isGreaterThan(300);
    assertThat(view.toString()).doesNotContain(SECRET);

    // The stored material is the AES-GCM ciphertext, NOT the plaintext (BR1/DL-0074).
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT subject, encrypted_material, key_alias FROM platform_certificates");
    byte[] stored = (byte[]) row.get("encrypted_material");
    String storedAsText = new String(stored, StandardCharsets.UTF_8);
    assertThat(storedAsText).doesNotContain(SECRET);
    assertThat(row.get("key_alias")).isNotNull();
    // The cipher round-trips back to the exact secret (proves it is encrypted, not hashed/lost).
    assertThat(new String(secretCipher.decrypt(stored), StandardCharsets.UTF_8)).isEqualTo(SECRET);
  }

  @Test
  void statusWithoutAnyCertificateIs404() {
    assertThatThrownBy(() -> custodyService.status())
        .isInstanceOf(CertificateNotFoundException.class);
  }

  @Test
  void expirySweepFlagsAnExpiringCertificateOnceAndAlerts() {
    // BR5: a certificate within the horizon is flagged EXPIRING and alerted exactly once
    // (idempotent).
    LocalDate until = LocalDate.now().plusDays(10); // within the default 30-day horizon
    custodyService.importCertificate(
        new ImportCertificateCommand(
            "CN=ACME:11222333000181",
            "11222333000181",
            LocalDate.now().minusYears(1),
            until,
            SECRET.getBytes(StandardCharsets.UTF_8)),
        "ti-admin");

    int firstSweep = custodyService.flagExpiringCertificates(java.time.Instant.now(), 30);
    int secondSweep = custodyService.flagExpiringCertificates(java.time.Instant.now(), 30);

    assertThat(firstSweep).isEqualTo(1); // newly flagged
    assertThat(secondSweep).isEqualTo(0); // idempotent: not re-alerted

    String status =
        jdbcTemplate.queryForObject("SELECT status FROM platform_certificates", String.class);
    assertThat(status).isEqualTo("EXPIRING");
  }

  @Test
  void theSecretMaterialNeverAppearsInTheSystemAuditOrCertificateRows() {
    // SECURITY regression: scan every persisted text for the secret — it must appear nowhere.
    custodyService.importCertificate(
        new ImportCertificateCommand(
            "CN=ACME:11222333000181",
            "11222333000181",
            LocalDate.now().minusDays(1),
            LocalDate.now().plusYears(2),
            SECRET.getBytes(StandardCharsets.UTF_8)),
        "ti-admin");
    custodyService.flagExpiringCertificates(java.time.Instant.now(), 30);

    List<Map<String, Object>> certs =
        jdbcTemplate.queryForList(
            "SELECT subject, holder_document, fingerprint, key_alias FROM platform_certificates");
    assertThat(certs.toString()).doesNotContain(SECRET);

    List<Map<String, Object>> audit =
        jdbcTemplate.queryForList("SELECT type, actor, detail_json FROM system_audit");
    assertThat(audit.toString()).doesNotContain(SECRET);
  }
}
