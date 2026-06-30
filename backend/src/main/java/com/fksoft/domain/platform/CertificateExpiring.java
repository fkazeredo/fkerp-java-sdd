package com.fksoft.domain.platform;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Domain event: a custodied e-CNPJ certificate is approaching (or past) its validity end (SPEC-0023
 * BR5/Events). Carries <strong>only metadata</strong> — never the certificate material (BR1).
 * Consumed by governance/TI and the system audit (DL-0077) so the fiscal/operational risk of an
 * expiring certificate is visible.
 *
 * @param fingerprint the certificate thumbprint (identification, not secret)
 * @param validUntil the validity end date
 * @param daysToExpiry whole days to expiry at the evaluation instant (may be negative)
 * @param occurredAt when the alert was raised
 */
public record CertificateExpiring(
    String fingerprint, LocalDate validUntil, long daysToExpiry, Instant occurredAt) {}
