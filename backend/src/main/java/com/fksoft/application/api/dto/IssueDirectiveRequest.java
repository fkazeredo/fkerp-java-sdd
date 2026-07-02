package com.fksoft.application.api.dto;

import com.fksoft.domain.commercialpolicy.DefineRuleCommand;
import com.fksoft.domain.commercialpolicy.ParameterKey;
import com.fksoft.domain.commercialpolicy.ParameterLayer;
import com.fksoft.domain.commercialpolicy.ParameterScope;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to issue a director's directive (SPEC-0014 {@code POST
 * /api/commercial-policy/directives}). The layer is always {@code DIRECTIVE} (top of precedence); a
 * {@code justification} is mandatory and the caller must hold the director role (BR5, DL-0038).
 *
 * @param key the parameter key
 * @param value the value text (must parse for {@code type})
 * @param type the value type
 * @param accountId scope: account, or {@code null} for any
 * @param productRef scope: product reference, or {@code null} for any
 * @param channel scope: sales channel, or {@code null} for any
 * @param validFrom effectivity start, or {@code null} for today
 * @param validUntil effectivity end, or {@code null} for open-ended
 * @param justification the mandatory justification (the director's reason)
 */
public record IssueDirectiveRequest(
    @NotBlank String key,
    @NotBlank String value,
    @NotBlank String type,
    UUID accountId,
    String productRef,
    String channel,
    LocalDate validFrom,
    LocalDate validUntil,
    @NotBlank String justification) {

  /** Translates this request to the domain command at the {@code DIRECTIVE} layer. */
  public DefineRuleCommand toCommand() {
    return new DefineRuleCommand(
        ParameterKey.parse(key),
        ParameterLayer.DIRECTIVE,
        new ParameterScope(accountId, productRef, channel),
        value,
        type,
        validFrom,
        validUntil,
        justification);
  }
}
