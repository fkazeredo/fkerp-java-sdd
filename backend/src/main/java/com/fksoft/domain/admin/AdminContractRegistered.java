package com.fksoft.domain.admin;

import java.time.Instant;

/**
 * Domain event: an administrative contract was registered for a supplier (SPEC-0025 Events).
 * In-process; consumed for traceability/DSS. Carries the supplier reference (value) — no personal
 * data.
 *
 * @param supplierRef the supplier id the contract covers (value, as text)
 * @param occurredAt when it was registered
 */
public record AdminContractRegistered(String supplierRef, Instant occurredAt) {}
