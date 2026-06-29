package com.fksoft.domain.commercialpolicy;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a governed parameter rule was defined (SPEC-0014 Events). Published in-process by
 * {@code commercial-policy}; a consumer such as {@code intelligence} can learn which rule changed a
 * margin. Carries only values (no entity) so the boundary stays clean.
 *
 * @param ruleId the new rule's id
 * @param key the parameter key
 * @param layer the governance layer
 * @param scope the rule's scope matcher
 * @param occurredAt when the rule was defined
 */
public record ParameterRuleDefined(
    UUID ruleId,
    ParameterKey key,
    ParameterLayer layer,
    ParameterScope scope,
    Instant occurredAt) {}
