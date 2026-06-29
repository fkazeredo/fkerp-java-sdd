package com.fksoft.domain.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NoShowPolicy} (SPEC-0010 BR6): charges the fee, waives it with proof when
 * the policy allows, and charges nothing when no fee is configured. Pure domain.
 */
class NoShowPolicyTest {

  private static final Money FEE = Money.of(new BigDecimal("90.00"), "BRL");

  @Test
  void chargesTheFeeWhenNoProofIsGiven() {
    NoShowPolicy policy = new NoShowPolicy(FEE, true);

    assertThat(policy.chargeFor(false)).isEqualTo(FEE);
    assertThat(policy.isWaived(false)).isFalse();
  }

  @Test
  void waivesTheFeeWithProofWhenThePolicyAllows() {
    NoShowPolicy policy = new NoShowPolicy(FEE, true);

    assertThat(policy.chargeFor(true)).isNull();
    assertThat(policy.isWaived(true)).isTrue();
  }

  @Test
  void doesNotWaiveWithProofWhenThePolicyDoesNotAllowIt() {
    NoShowPolicy policy = new NoShowPolicy(FEE, false);

    assertThat(policy.chargeFor(true)).isEqualTo(FEE);
    assertThat(policy.isWaived(true)).isFalse();
  }

  @Test
  void chargesNothingWhenNoFeeIsConfigured() {
    assertThat(NoShowPolicy.none().chargeFor(false)).isNull();
    assertThat(NoShowPolicy.none().isWaived(true)).isFalse();
  }
}
