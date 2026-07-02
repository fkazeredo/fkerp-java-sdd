package com.fksoft.domain.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the retention table (SPEC-0008 BR2) and the purge veto (BR7): fiscal documents
 * keep 5 years, contracts 10 years, and a purge before the deadline is rejected.
 */
class RetentionPolicyTest {

  private static final Instant NOW = Instant.parse("2026-06-26T12:00:00Z");

  @Test
  void fiscalDocumentRetainsFiveYears() {
    LocalDate issued = LocalDate.of(2026, 6, 20);

    assertThat(RetentionPolicy.retentionUntil(DocumentTypeCodes.NFSE, issued))
        .isEqualTo(LocalDate.of(2031, 6, 20));
    assertThat(RetentionPolicy.retentionUntil(DocumentTypeCodes.COMMISSION_INVOICE, issued))
        .isEqualTo(LocalDate.of(2031, 6, 20));
  }

  @Test
  void contractRetainsTenYears() {
    LocalDate issued = LocalDate.of(2026, 6, 20);

    assertThat(RetentionPolicy.retentionUntil(DocumentTypeCodes.REPRESENTATION_CONTRACT, issued))
        .isEqualTo(LocalDate.of(2036, 6, 20));
  }

  @Test
  void documentComputesRetentionUntilOnIngestion() {
    Document document =
        Document.ingest(
            DocumentTypeCodes.NFSE,
            "ref-1",
            "sha256:abc",
            LocalDate.of(2026, 6, 20),
            null,
            false,
            NOW,
            "tester");

    assertThat(document.retentionUntil()).isEqualTo(LocalDate.of(2031, 6, 20));
  }

  @Test
  void purgeIsRejectedWithinRetention() {
    Document document =
        Document.ingest(
            DocumentTypeCodes.NFSE,
            "ref-1",
            "sha256:abc",
            LocalDate.of(2026, 6, 20),
            null,
            false,
            NOW,
            "tester");

    assertThatThrownBy(() -> document.ensurePurgeable(LocalDate.of(2030, 1, 1)))
        .isInstanceOf(ComplianceRetentionNotExpiredException.class);
  }

  @Test
  void purgeIsAllowedAfterRetention() {
    Document document =
        Document.ingest(
            DocumentTypeCodes.NFSE,
            "ref-1",
            "sha256:abc",
            LocalDate.of(2026, 6, 20),
            null,
            false,
            NOW,
            "tester");

    assertThatCode(() -> document.ensurePurgeable(LocalDate.of(2031, 6, 20)))
        .doesNotThrowAnyException();
  }
}
