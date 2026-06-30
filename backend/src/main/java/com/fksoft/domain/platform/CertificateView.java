package com.fksoft.domain.platform;

import java.time.LocalDate;

/**
 * Public, secret-free read view of a custodied certificate (SPEC-0023 BR1). Exposes <strong>only
 * metadata</strong> — the certificate material (private key / password) is NEVER part of any view,
 * DTO, event or log (BR1, security.md). {@code daysToExpiry} is computed at read time against the
 * evaluation instant.
 *
 * @param subject the certificate subject DN
 * @param holderDocument the holder CNPJ (caller masks it when logging)
 * @param fingerprint the SHA-256 thumbprint of the certificate (audit/identification)
 * @param validFrom validity start (calendar date)
 * @param validUntil validity end (calendar date)
 * @param daysToExpiry whole days from the evaluation date to {@code validUntil} (may be negative)
 * @param status the derived lifecycle status
 */
public record CertificateView(
    String subject,
    String holderDocument,
    String fingerprint,
    LocalDate validFrom,
    LocalDate validUntil,
    long daysToExpiry,
    CertificateStatus status) {}
