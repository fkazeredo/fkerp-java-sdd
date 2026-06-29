package com.fksoft.domain.commercialpolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The provenance of a resolved parameter (SPEC-0014 BR2, redesign 7.3): <strong>which layer won,
 * who defined it and when</strong>. Returned alongside every resolved value so a number can always
 * be traced to a directive/promotion/contract/policy/default — never to a bug.
 *
 * @param layer the winning governance layer
 * @param ruleId the id of the winning rule
 * @param definedBy who authored the winning rule (audit)
 * @param definedAt when the winning rule was created (audit)
 * @param validUntil the rule's effectivity end, or {@code null} when open-ended
 */
public record Provenance(
    ParameterLayer layer, UUID ruleId, String definedBy, Instant definedAt, LocalDate validUntil) {}
