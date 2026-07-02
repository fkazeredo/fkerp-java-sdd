package com.fksoft.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based invariants of the {@link Money} kernel (Fase 19i/DL-0132, jqwik): for <em>any</em>
 * amount — not just the examples unit tests pick — construction normalizes to scale 2 with HALF_UP,
 * add/subtract are exact inverses, addition is commutative, zero is the identity and mixing
 * currencies always throws. These are the invariants every money-touching module
 * (commissioning/exchange/finance/payout) silently relies on.
 */
class MoneyPropertyTest {

  @Provide
  Arbitrary<BigDecimal> amounts() {
    // Up to 6 decimal places over a wide range, positive and negative (rates use scale 6).
    return Arbitraries.bigDecimals()
        .between(new BigDecimal("-9999999999.999999"), new BigDecimal("9999999999.999999"))
        .ofScale(6);
  }

  @Property
  void constructionAlwaysNormalizesToScale2HalfUp(@ForAll("amounts") BigDecimal raw) {
    Money money = Money.of(raw, "BRL");
    assertThat(money.amount().scale()).isEqualTo(2);
    assertThat(money.amount()).isEqualByComparingTo(raw.setScale(2, RoundingMode.HALF_UP));
  }

  @Property
  void addThenSubtractIsTheExactInverse(
      @ForAll("amounts") BigDecimal a, @ForAll("amounts") BigDecimal b) {
    Money left = Money.of(a, "BRL");
    Money right = Money.of(b, "BRL");
    assertThat(left.add(right).subtract(right)).isEqualTo(left);
  }

  @Property
  void additionIsCommutative(@ForAll("amounts") BigDecimal a, @ForAll("amounts") BigDecimal b) {
    Money left = Money.of(a, "BRL");
    Money right = Money.of(b, "BRL");
    assertThat(left.add(right)).isEqualTo(right.add(left));
  }

  @Property
  void zeroIsTheAdditiveIdentity(@ForAll("amounts") BigDecimal a) {
    Money money = Money.of(a, "BRL");
    assertThat(money.add(Money.zero("BRL"))).isEqualTo(money);
  }

  @Property
  void mixingCurrenciesAlwaysThrows(@ForAll("amounts") BigDecimal a) {
    Money brl = Money.of(a, "BRL");
    Money usd = Money.of(a, "USD");
    assertThatThrownBy(() -> brl.add(usd)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> brl.subtract(usd)).isInstanceOf(IllegalArgumentException.class);
  }

  @Property
  void multiplyKeepsTheCurrencyAndScale(@ForAll("amounts") BigDecimal a) {
    Money money = Money.of(a, "USD").multiply(new BigDecimal("0.135"));
    assertThat(money.currency()).isEqualTo("USD");
    assertThat(money.amount().scale()).isEqualTo(2);
  }
}
