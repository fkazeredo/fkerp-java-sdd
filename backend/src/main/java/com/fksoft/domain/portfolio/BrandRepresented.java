package com.fksoft.domain.portfolio;

import java.time.Instant;

/**
 * Business fact: a brand started being represented (SPEC-0020 Events). Published in-process when a
 * {@code RepresentedBrand} is registered. Consumers: Intelligence (supplier leverage, redesign
 * 8.2-E) and CommercialPolicy (scope). Carries the brandRef (value) — a brand/supplier identifier,
 * not personal data.
 *
 * @param brandRef the represented brand identifier (value)
 * @param occurredAt when the representation was registered
 */
public record BrandRepresented(String brandRef, Instant occurredAt) {}
