package com.fksoft.domain.platform;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a system-audit entry (SPEC-0023 — {@code GET /audit}). The {@code detail} is
 * a metadata-only JSON string — it NEVER contains secret material (BR1).
 *
 * @param id the entry id
 * @param occurredAt when the fact happened
 * @param actor who (or {@code null} for system), already masked
 * @param type the audit type
 * @param detail the metadata JSON (no secrets)
 * @param correlationId the correlation id, or {@code null}
 */
public record SystemAuditView(
    UUID id,
    Instant occurredAt,
    String actor,
    AuditType type,
    String detail,
    String correlationId) {}
