package com.fksoft.domain.admin;

import java.time.Instant;

/**
 * Domain event: an administrative supplier was registered (SPEC-0025 Events). In-process; consumed
 * for traceability/DSS (fixed cost). Carries the supplier reference (value) — no full identifier,
 * so a CNPJ/CPF (possible personal data of a self-employed) never travels in the event.
 *
 * @param supplierRef the supplier id (value, as text)
 * @param occurredAt when it was registered
 */
public record AdminSupplierRegistered(String supplierRef, Instant occurredAt) {}
