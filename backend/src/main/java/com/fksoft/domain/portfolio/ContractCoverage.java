package com.fksoft.domain.portfolio;

import java.time.LocalDate;

/**
 * Read-model answer to "does this brand have an in-force representation contract on a given date?"
 * (SPEC-0020 BR2; DL-0061). It is informative only — the Portfolio <strong>alerts, it never
 * blocks</strong> a sale; whoever composes the sale (Quoting/Booking) may consult this and signal
 * the exception. Carries no personal data.
 *
 * @param brandRef the brand checked (value)
 * @param on the date the coverage was evaluated at
 * @param covered {@code true} when at least one contract is in force on that date
 */
public record ContractCoverage(String brandRef, LocalDate on, boolean covered) {}
