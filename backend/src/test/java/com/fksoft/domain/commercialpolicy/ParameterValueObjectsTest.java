package com.fksoft.domain.commercialpolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the CommercialPolicy value objects (SPEC-0014 BR1): key format, scope specificity
 * and matching (the wildcard semantics), and value-type validation.
 */
class ParameterValueObjectsTest {

  @Test
  void parameterKeyParsesUpperSnakeCaseAndRejectsMalformed() {
    assertThat(ParameterKey.parse("markup_pct").value()).isEqualTo("MARKUP_PCT");
    assertThat(ParameterKey.parse(" FX_DRIFT_LIMIT ").value()).isEqualTo("FX_DRIFT_LIMIT");
    assertThatThrownBy(() -> ParameterKey.parse("bad-key"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ParameterKey.parse("")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void scopeSpecificityCountsFixedDimensions() {
    assertThat(ParameterScope.global().specificity()).isZero();
    assertThat(ParameterScope.ofAccount(UUID.randomUUID()).specificity()).isEqualTo(1);
    assertThat(new ParameterScope(UUID.randomUUID(), "CAR-MCO", null).specificity()).isEqualTo(2);
    assertThat(new ParameterScope(UUID.randomUUID(), "CAR-MCO", "WEB").specificity()).isEqualTo(3);
  }

  @Test
  void globalRuleMatchesAnyQueryButSpecificRuleOnlyMatchesItsScope() {
    UUID account = UUID.randomUUID();
    ParameterScope query = ParameterScope.ofAccount(account);

    assertThat(ParameterScope.global().matches(query)).isTrue();
    assertThat(ParameterScope.ofAccount(account).matches(query)).isTrue();
    assertThat(ParameterScope.ofAccount(UUID.randomUUID()).matches(query)).isFalse();
    // A rule fixing a product the query does not carry does not match (wildcard is on the rule
    // side).
    assertThat(new ParameterScope(account, "CAR-MCO", null).matches(query)).isFalse();
  }

  @Test
  void blankScopeDimensionsAreNormalizedToWildcard() {
    ParameterScope scope = new ParameterScope(null, "  ", "");
    assertThat(scope.productRef()).isNull();
    assertThat(scope.channel()).isNull();
    assertThat(scope.specificity()).isZero();
  }

  @Test
  void valueTypeValidatesItsText() {
    assertThat(ParameterValueType.PERCENT.isValid("0.12")).isTrue();
    assertThat(ParameterValueType.MONEY.isValid("1.00")).isTrue();
    assertThat(ParameterValueType.NUMBER.isValid("5")).isTrue();
    assertThat(ParameterValueType.BOOL.isValid("true")).isTrue();
    assertThat(ParameterValueType.BOOL.isValid("FALSE")).isTrue();

    assertThat(ParameterValueType.PERCENT.isValid("abc")).isFalse();
    assertThat(ParameterValueType.MONEY.isValid("")).isFalse();
    assertThat(ParameterValueType.BOOL.isValid("yes")).isFalse();
    assertThat(ParameterValueType.NUMBER.isValid(null)).isFalse();
  }

  @Test
  void markupDecisionFromResolvedCarriesWinningLayerAsSource() {
    ResolvedParameter resolved =
        new ResolvedParameter(
            ParameterKey.MARKUP_PCT,
            "0.12",
            ParameterValueType.PERCENT,
            new Provenance(ParameterLayer.PROMOTION, UUID.randomUUID(), "diretor.ana", null, null));

    MarkupDecision decision = MarkupDecision.from(resolved);

    assertThat(decision.pct()).isEqualByComparingTo("0.12");
    assertThat(decision.source()).isEqualTo("PROMOTION");
  }
}
