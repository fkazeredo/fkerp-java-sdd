package com.fksoft.domain.commercialpolicy.internal;

import com.fksoft.domain.commercialpolicy.ParameterKey;
import com.fksoft.domain.commercialpolicy.ParameterLayer;
import com.fksoft.domain.commercialpolicy.ParameterRuleView;
import com.fksoft.domain.commercialpolicy.ParameterScope;
import com.fksoft.domain.commercialpolicy.ParameterValueType;
import com.fksoft.domain.commercialpolicy.Provenance;
import com.fksoft.domain.commercialpolicy.ResolvedParameter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A governed parameter rule (SPEC-0014 BR1): a value for a {@code parameterKey} at a {@code layer},
 * with a scope matcher (account/product/channel), effectivity and audit. Module-internal — other
 * modules read resolved values through the {@code CommercialPolicyService} / {@code MarkupProvider}
 * ports, never this entity (modules-and-apis.md).
 *
 * <p>The matcher dimensions are plain values (no cross-context FK). The service guarantees
 * value/type coherence and effectivity before building the entity (Validation Rules); the entity
 * exposes the domain queries the engine needs ({@link #isActiveOn}, {@link #matches}, {@link
 * #specificity}).
 */
@Entity
@Table(name = "parameter_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParameterRule {

  @Id private UUID id;

  @Column(name = "parameter_key")
  private String parameterKey;

  @Enumerated(EnumType.STRING)
  private ParameterLayer layer;

  @Column(name = "scope_account_id")
  private UUID scopeAccountId;

  @Column(name = "scope_product_ref")
  private String scopeProductRef;

  @Column(name = "scope_channel")
  private String scopeChannel;

  @Column(name = "value_text")
  private String valueText;

  @Enumerated(EnumType.STRING)
  @Column(name = "value_type")
  private ParameterValueType valueType;

  @Column(name = "valid_from")
  private LocalDate validFrom;

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Column(name = "defined_by")
  private String definedBy;

  private String justification;

  private Instant createdAt;

  private Instant updatedAt;

  private long version;

  /**
   * Defines a new rule. The caller (service) has already validated value/type and effectivity.
   *
   * @param key the parameter key
   * @param layer the governance layer
   * @param scope the scope matcher
   * @param valueText the value text
   * @param valueType the value type
   * @param validFrom effectivity start
   * @param validUntil effectivity end, or {@code null} for open-ended
   * @param definedBy who authored it (audit)
   * @param justification justification (mandatory for directives), or {@code null}
   * @param now creation instant (UTC)
   * @return a new, persistable rule
   */
  public static ParameterRule define(
      ParameterKey key,
      ParameterLayer layer,
      ParameterScope scope,
      String valueText,
      ParameterValueType valueType,
      LocalDate validFrom,
      LocalDate validUntil,
      String definedBy,
      String justification,
      Instant now) {
    ParameterRule rule = new ParameterRule();
    rule.id = UUID.randomUUID();
    rule.parameterKey = key.value();
    rule.layer = layer;
    rule.scopeAccountId = scope.accountId();
    rule.scopeProductRef = scope.productRef();
    rule.scopeChannel = scope.channel();
    rule.valueText = valueText;
    rule.valueType = valueType;
    rule.validFrom = validFrom;
    rule.validUntil = validUntil;
    rule.definedBy = definedBy;
    rule.justification = justification;
    rule.createdAt = now;
    rule.updatedAt = now;
    rule.version = 0L;
    return rule;
  }

  /** The rule's scope matcher as a value object. */
  public ParameterScope scope() {
    return new ParameterScope(scopeAccountId, scopeProductRef, scopeChannel);
  }

  /** Whether the rule is in effect on {@code date} (validFrom &le; date &le; validUntil, BR2). */
  public boolean isActiveOn(LocalDate date) {
    if (date.isBefore(validFrom)) {
      return false;
    }
    return validUntil == null || !date.isAfter(validUntil);
  }

  /** Whether this rule's scope applies to the queried scope (BR3). */
  public boolean matches(ParameterScope query) {
    return scope().matches(query);
  }

  /** The number of fixed scope dimensions — intra-layer specificity (BR3). */
  public int specificity() {
    return scope().specificity();
  }

  /** Projects the resolved value + provenance for this winning rule (BR2). */
  public ResolvedParameter toResolved() {
    return new ResolvedParameter(
        new ParameterKey(parameterKey),
        valueText,
        valueType,
        new Provenance(layer, id, definedBy, createdAt, validUntil));
  }

  /** Projects the public read view (rules listing for audit/curation). */
  public ParameterRuleView toView() {
    return new ParameterRuleView(
        id,
        new ParameterKey(parameterKey),
        layer,
        scope(),
        valueText,
        valueType,
        validFrom,
        validUntil,
        definedBy,
        justification,
        createdAt);
  }
}
