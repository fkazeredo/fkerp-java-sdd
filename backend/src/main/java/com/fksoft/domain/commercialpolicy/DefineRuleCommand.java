package com.fksoft.domain.commercialpolicy;

import java.time.LocalDate;

/**
 * Command to define a governed parameter rule (SPEC-0014 {@code POST /rules} and {@code
 * /directives}). The application service validates effectivity and value-type coherence (Validation
 * Rules) and the authorization for the layer (BR5/BR7, DL-0038) before persisting.
 *
 * @param key the parameter key
 * @param layer the governance layer
 * @param scope the scope matcher (use {@link ParameterScope#global()} for global)
 * @param value the value text (must parse for {@code type})
 * @param type the value type
 * @param validFrom effectivity start (defaults to today when {@code null})
 * @param validUntil effectivity end, or {@code null} for open-ended (must be {@code >= validFrom})
 * @param justification mandatory for {@code DIRECTIVE} (BR5); optional otherwise
 */
public record DefineRuleCommand(
    ParameterKey key,
    ParameterLayer layer,
    ParameterScope scope,
    String value,
    ParameterValueType type,
    LocalDate validFrom,
    LocalDate validUntil,
    String justification) {}
