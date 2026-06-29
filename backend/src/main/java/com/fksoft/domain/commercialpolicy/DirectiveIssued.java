package com.fksoft.domain.commercialpolicy;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a director's directive was issued (SPEC-0014 Events, BR5) — reinforced audit.
 * Unlike {@link ParameterRuleDefined}, it carries the mandatory {@code justification}
 * (who/why/when), so the audit trail shows the number came from a deliberate order. Published
 * in-process by {@code commercial-policy}.
 *
 * @param ruleId the directive rule's id
 * @param key the parameter key
 * @param definedBy who issued the directive (the director)
 * @param justification the mandatory justification (BR5)
 * @param occurredAt when the directive was issued
 */
public record DirectiveIssued(
    UUID ruleId, ParameterKey key, String definedBy, String justification, Instant occurredAt) {}
