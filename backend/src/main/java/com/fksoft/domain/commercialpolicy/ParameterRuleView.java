package com.fksoft.domain.commercialpolicy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public read view of a {@link com.fksoft.domain.commercialpolicy.ParameterRule} (SPEC-0014 API:
 * rules listing for audit/curation). Entity-free projection — the entity never leaves the module
 * (modules-and-apis.md).
 *
 * @param id the rule id
 * @param key the parameter key
 * @param layer the governance layer
 * @param scope the scope matcher
 * @param value the value text
 * @param type the value type
 * @param validFrom effectivity start
 * @param validUntil effectivity end, or {@code null} when open-ended
 * @param definedBy who authored the rule (audit)
 * @param justification justification (present for directives), or {@code null}
 * @param createdAt when the rule was created (audit)
 */
public record ParameterRuleView(
    UUID id,
    ParameterKey key,
    ParameterLayer layer,
    ParameterScope scope,
    String value,
    ParameterValueType type,
    LocalDate validFrom,
    LocalDate validUntil,
    String definedBy,
    String justification,
    Instant createdAt) {}
