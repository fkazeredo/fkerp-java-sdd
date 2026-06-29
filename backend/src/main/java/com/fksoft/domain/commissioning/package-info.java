/**
 * Commissioning module (SPEC-0004): computes the two-sided commission — the supplier override (to
 * receive) and the agent commission (to pay) — and the derived spread, from a commissionable base
 * and two fixed percentages. Pure calculation, no persistence.
 *
 * <p>Spring Modulith application module. The public API is the {@link
 * com.fksoft.domain.commissioning.CommissionCalculator} port (consumed in-process by Quoting,
 * SPEC-0005) and its value records ({@link com.fksoft.domain.commissioning.CommissionInput}, {@link
 * com.fksoft.domain.commissioning.CommissionStatement}).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Commissioning")
package com.fksoft.domain.commissioning;
