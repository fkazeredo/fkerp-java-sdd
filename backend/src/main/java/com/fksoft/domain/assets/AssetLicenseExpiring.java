package com.fksoft.domain.assets;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Governance/IT alert: a software license is expiring (or has expired) — published once per license
 * by the controlled-clock sweep (SPEC-0021 BR3/Events; DL-0066). It is an <strong>alert, not a
 * block</strong>: the system signals so governance/IT can renew, but nothing is vetoed. Carries
 * only values (no personal data).
 *
 * @param assetId the license asset's id (value)
 * @param expiresAt the license expiry date
 * @param occurredAt when the alert was raised (UTC)
 */
public record AssetLicenseExpiring(UUID assetId, LocalDate expiresAt, Instant occurredAt) {}
