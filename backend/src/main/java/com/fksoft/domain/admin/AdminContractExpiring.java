package com.fksoft.domain.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Governance alert: an administrative contract is expiring (or has expired) — published once per
 * contract by the controlled-clock job (SPEC-0025 BR5/Events; DL-0087). It is an <strong>alert, not
 * a block</strong>: the system signals so governance/DSS can act, but nothing is vetoed. Carries
 * the contract id and the {@code validUntil} (values) — no personal data.
 *
 * @param contractId the contract that is expiring
 * @param validUntil the contract's expiry date
 * @param occurredAt when the alert was raised
 */
public record AdminContractExpiring(UUID contractId, LocalDate validUntil, Instant occurredAt) {}
