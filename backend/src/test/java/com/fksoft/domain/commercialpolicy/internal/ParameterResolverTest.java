package com.fksoft.domain.commercialpolicy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.commercialpolicy.ParameterKey;
import com.fksoft.domain.commercialpolicy.ParameterLayer;
import com.fksoft.domain.commercialpolicy.ParameterScope;
import com.fksoft.domain.commercialpolicy.ParameterValueType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure precedence engine (SPEC-0014 BR2/BR3/BR8, DL-0037) with exact fixtures:
 * each layer overrides the lower ones (even with a less specific scope), specificity decides within
 * a layer, the tie-break is deterministic, and the fallback is the SYSTEM_DEFAULT. No DB — the
 * resolver is fed a candidate list directly.
 */
class ParameterResolverTest {

  private static final ParameterKey KEY = ParameterKey.MARKUP_PCT;
  private static final UUID ACCOUNT = UUID.fromString("8f1c0000-0000-0000-0000-000000000001");
  private static final UUID OTHER_ACCOUNT = UUID.fromString("8f1c0000-0000-0000-0000-000000000002");
  private static final String PRODUCT = "CAR-MCO";
  private static final LocalDate TODAY = LocalDate.of(2026, 6, 29);
  private static final Instant NOW = Instant.parse("2026-06-01T09:00:00Z");

  @Test
  void fallsBackToSystemDefaultWhenNoOtherRuleApplies() {
    var defaultRule = rule(ParameterLayer.SYSTEM_DEFAULT, ParameterScope.global(), "0", NOW);

    var winner =
        ParameterResolver.resolve(List.of(defaultRule), TODAY, ParameterScope.ofAccount(ACCOUNT));

    assertThat(winner).contains(defaultRule);
    assertThat(winner.orElseThrow().layer()).isEqualTo(ParameterLayer.SYSTEM_DEFAULT);
  }

  @Test
  void higherLayerWinsEvenWithLessSpecificScope() {
    // DIRECTIVE at GLOBAL (specificity 0) must beat a POLICY pinned to the account (specificity 1).
    var policyForAccount = rule(ParameterLayer.POLICY, ParameterScope.ofAccount(ACCOUNT), "5", NOW);
    var directiveGlobal = rule(ParameterLayer.DIRECTIVE, ParameterScope.global(), "8", NOW);
    var systemDefault = rule(ParameterLayer.SYSTEM_DEFAULT, ParameterScope.global(), "0", NOW);

    var winner =
        ParameterResolver.resolve(
            List.of(systemDefault, policyForAccount, directiveGlobal),
            TODAY,
            ParameterScope.ofAccount(ACCOUNT));

    assertThat(winner.orElseThrow().layer()).isEqualTo(ParameterLayer.DIRECTIVE);
    assertThat(winner.orElseThrow().valueText()).isEqualTo("8");
  }

  @Test
  void promotionBeatsPolicyEvenWhenPolicyIsMoreSpecific() {
    var policyForProduct =
        rule(ParameterLayer.POLICY, new ParameterScope(ACCOUNT, PRODUCT, null), "5", NOW);
    var promotionGlobal = rule(ParameterLayer.PROMOTION, ParameterScope.global(), "12", NOW);

    var winner =
        ParameterResolver.resolve(
            List.of(policyForProduct, promotionGlobal),
            TODAY,
            new ParameterScope(ACCOUNT, PRODUCT, null));

    assertThat(winner.orElseThrow().layer()).isEqualTo(ParameterLayer.PROMOTION);
    assertThat(winner.orElseThrow().valueText()).isEqualTo("12");
  }

  @Test
  void moreSpecificScopeWinsWithinTheSameLayer() {
    // Within POLICY: product+account (specificity 2) > account (1) > global (0).
    var global = rule(ParameterLayer.POLICY, ParameterScope.global(), "3", NOW);
    var byAccount = rule(ParameterLayer.POLICY, ParameterScope.ofAccount(ACCOUNT), "5", NOW);
    var byProduct =
        rule(ParameterLayer.POLICY, new ParameterScope(ACCOUNT, PRODUCT, null), "7", NOW);

    var winner =
        ParameterResolver.resolve(
            List.of(global, byAccount, byProduct),
            TODAY,
            new ParameterScope(ACCOUNT, PRODUCT, null));

    assertThat(winner.orElseThrow().valueText()).isEqualTo("7");
  }

  @Test
  void doesNotMatchRuleScopedToAnotherAccount() {
    var otherAccount =
        rule(ParameterLayer.POLICY, ParameterScope.ofAccount(OTHER_ACCOUNT), "9", NOW);
    var systemDefault = rule(ParameterLayer.SYSTEM_DEFAULT, ParameterScope.global(), "0", NOW);

    var winner =
        ParameterResolver.resolve(
            List.of(otherAccount, systemDefault), TODAY, ParameterScope.ofAccount(ACCOUNT));

    assertThat(winner.orElseThrow().layer()).isEqualTo(ParameterLayer.SYSTEM_DEFAULT);
  }

  @Test
  void ignoresRulesNotInEffectOnTheResolutionDate() {
    var expired =
        ruleWithEffectivity(
            ParameterLayer.PROMOTION,
            ParameterScope.global(),
            "20",
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31),
            NOW);
    var systemDefault = rule(ParameterLayer.SYSTEM_DEFAULT, ParameterScope.global(), "0", NOW);

    var winner =
        ParameterResolver.resolve(List.of(expired, systemDefault), TODAY, ParameterScope.global());

    assertThat(winner.orElseThrow().layer()).isEqualTo(ParameterLayer.SYSTEM_DEFAULT);
  }

  @Test
  void tieBreakIsDeterministic_newerValidFromWins() {
    // Same layer, same specificity, both active: the one with the newer validFrom wins (BR8).
    var older =
        ruleWithEffectivity(
            ParameterLayer.POLICY,
            ParameterScope.ofAccount(ACCOUNT),
            "5",
            LocalDate.of(2026, 1, 1),
            null,
            NOW);
    var newer =
        ruleWithEffectivity(
            ParameterLayer.POLICY,
            ParameterScope.ofAccount(ACCOUNT),
            "6",
            LocalDate.of(2026, 6, 1),
            null,
            NOW.plusSeconds(1));

    var winnerOrder1 =
        ParameterResolver.resolve(List.of(older, newer), TODAY, ParameterScope.ofAccount(ACCOUNT));
    var winnerOrder2 =
        ParameterResolver.resolve(List.of(newer, older), TODAY, ParameterScope.ofAccount(ACCOUNT));

    assertThat(winnerOrder1.orElseThrow().valueText()).isEqualTo("6");
    // Order of the candidate list must not change the winner (determinism).
    assertThat(winnerOrder2.orElseThrow().valueText()).isEqualTo("6");
  }

  @Test
  void resolvesEmptyWhenNothingActiveOrMatching() {
    var otherAccount =
        rule(ParameterLayer.POLICY, ParameterScope.ofAccount(OTHER_ACCOUNT), "9", NOW);

    var winner =
        ParameterResolver.resolve(List.of(otherAccount), TODAY, ParameterScope.ofAccount(ACCOUNT));

    assertThat(winner).isEmpty();
  }

  private static ParameterRule rule(
      ParameterLayer layer, ParameterScope scope, String value, Instant createdAt) {
    return ruleWithEffectivity(layer, scope, value, LocalDate.of(2026, 1, 1), null, createdAt);
  }

  private static ParameterRule ruleWithEffectivity(
      ParameterLayer layer,
      ParameterScope scope,
      String value,
      LocalDate validFrom,
      LocalDate validUntil,
      Instant createdAt) {
    return ParameterRule.define(
        KEY,
        layer,
        scope,
        value,
        ParameterValueType.PERCENT,
        validFrom,
        validUntil,
        "tester",
        layer == ParameterLayer.DIRECTIVE ? "justification" : null,
        createdAt);
  }
}
