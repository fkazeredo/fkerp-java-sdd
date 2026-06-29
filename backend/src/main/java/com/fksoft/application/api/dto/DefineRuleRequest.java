package com.fksoft.application.api.dto;

import com.fksoft.domain.commercialpolicy.DefineRuleCommand;
import com.fksoft.domain.commercialpolicy.ParameterKey;
import com.fksoft.domain.commercialpolicy.ParameterLayer;
import com.fksoft.domain.commercialpolicy.ParameterScope;
import com.fksoft.domain.commercialpolicy.ParameterValueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request to define a governed rule (SPEC-0014 {@code POST /api/commercial-policy/rules}). The
 * layer is POLICY/PROMOTION/CONTRACT here; directives use the dedicated endpoint. Scope dimensions
 * are optional (omit for global). The service validates value/type and effectivity.
 *
 * @param key the parameter key (e.g. {@code MARKUP_PCT})
 * @param layer the governance layer (POLICY/PROMOTION/CONTRACT)
 * @param value the value text (must parse for {@code type})
 * @param type the value type (NUMBER/PERCENT/MONEY/BOOL)
 * @param accountId scope: account, or {@code null} for any
 * @param productRef scope: product reference, or {@code null} for any
 * @param channel scope: sales channel, or {@code null} for any
 * @param validFrom effectivity start, or {@code null} for today
 * @param validUntil effectivity end, or {@code null} for open-ended
 */
public record DefineRuleRequest(
    @NotBlank String key,
    @NotNull ParameterLayer layer,
    @NotBlank String value,
    @NotNull ParameterValueType type,
    UUID accountId,
    String productRef,
    String channel,
    LocalDate validFrom,
    LocalDate validUntil) {

  /**
   * Translates this request to the domain command (no justification — rules are not directives).
   */
  public DefineRuleCommand toCommand() {
    return new DefineRuleCommand(
        ParameterKey.parse(key),
        layer,
        new ParameterScope(accountId, productRef, channel),
        value,
        type,
        validFrom,
        validUntil,
        null);
  }
}
