package com.fksoft.application.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to record a human decision on an insight (SPEC-0013 BR4): the decision ({@code
 * ACCEPTED}/{@code REJECTED}/{@code DISMISSED}) and an optional note. Recording it does NOT trigger
 * any automatic action (BR2) — the DSS advises, the human decides. The decision value is validated
 * in the domain (an out-of-enum value yields {@code intelligence.decision.invalid} → 400).
 *
 * @param decision the human decision (validated against the enum)
 * @param note an optional free-text note
 */
public record DecideInsightRequest(@NotBlank String decision, String note) {}
