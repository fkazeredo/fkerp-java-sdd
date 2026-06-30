package com.fksoft.domain.platform;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A consolidated system-audit entry (SPEC-0023 BR4; DL-0077). <strong>Append-only</strong>: the
 * only way to create one is {@link #record}, and there is no mutator/update/delete — the trail
 * cannot be rewritten. The {@code detailJson} carries metadata ONLY (job, status, fingerprint,
 * daysToExpiry) — NEVER secret material (BR1, security.md). Module-internal.
 */
@Entity
@Table(name = "system_audit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class SystemAuditEntry {

  @Id private UUID id;

  private Instant occurredAt;
  private String actor;

  @Enumerated(EnumType.STRING)
  private AuditType type;

  @JdbcTypeCode(SqlTypes.JSON)
  private String detailJson;

  private String correlationId;

  /**
   * Records a new audit fact (append-only).
   *
   * @param type the audit type
   * @param actor who (or {@code null} for system) — caller masks any personal data
   * @param detailJson the metadata JSON (NO secret material)
   * @param correlationId the correlation id, or {@code null}
   * @param occurredAt when the fact happened
   * @return a new, persistable entry
   */
  public static SystemAuditEntry record(
      AuditType type, String actor, String detailJson, String correlationId, Instant occurredAt) {
    SystemAuditEntry entry = new SystemAuditEntry();
    entry.id = UUID.randomUUID();
    entry.type = type;
    entry.actor = actor;
    entry.detailJson = detailJson;
    entry.correlationId = correlationId;
    entry.occurredAt = occurredAt;
    return entry;
  }

  /** Projects to the public read view. */
  public SystemAuditView toView() {
    return new SystemAuditView(id, occurredAt, actor, type, detailJson, correlationId);
  }
}
