package com.fksoft.domain.portfolio;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Governance alert: a representation contract is expiring (or has expired) — published once per
 * contract by the controlled-clock job (SPEC-0020 BR5/Events; DL-0063). It is an <strong>alert, not
 * a block</strong>: the system signals so governance/DSS can act, but selling the brand is never
 * vetoed (DL-0061). Carries the brandRef and the {@code validUntil} (values) — no personal data.
 *
 * @param brandRef the brand whose contract is expiring (value)
 * @param validUntil the contract's expiry date
 * @param occurredAt when the alert was raised
 */
public record RepresentationExpiring(String brandRef, LocalDate validUntil, Instant occurredAt) {}
