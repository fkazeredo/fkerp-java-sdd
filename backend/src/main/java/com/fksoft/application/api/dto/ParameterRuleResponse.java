package com.fksoft.application.api.dto;

import com.fksoft.domain.commercialpolicy.ParameterRuleView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response for a governed rule (SPEC-0014 {@code POST /rules}, {@code /directives} and {@code GET
 * /rules}). Entity-free projection of {@link ParameterRuleView}.
 *
 * @param id the rule id
 * @param key the parameter key
 * @param layer the governance layer
 * @param accountId scope: account, or {@code null}
 * @param productRef scope: product reference, or {@code null}
 * @param channel scope: channel, or {@code null}
 * @param value the value text
 * @param type the value type
 * @param validFrom effectivity start
 * @param validUntil effectivity end, or {@code null}
 * @param definedBy who authored it (audit)
 * @param justification justification (for directives), or {@code null}
 * @param createdAt when it was created (audit)
 */
public record ParameterRuleResponse(
    UUID id,
    String key,
    String layer,
    UUID accountId,
    String productRef,
    String channel,
    String value,
    String type,
    LocalDate validFrom,
    LocalDate validUntil,
    String definedBy,
    String justification,
    Instant createdAt) {

  /** Maps a domain {@link ParameterRuleView} to the response. */
  public static ParameterRuleResponse from(ParameterRuleView view) {
    return new ParameterRuleResponse(
        view.id(),
        view.key().value(),
        view.layer().name(),
        view.scope().accountId(),
        view.scope().productRef(),
        view.scope().channel(),
        view.value(),
        view.type(),
        view.validFrom(),
        view.validUntil(),
        view.definedBy(),
        view.justification(),
        view.createdAt());
  }
}
