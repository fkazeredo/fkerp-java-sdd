package com.fksoft.domain.quoting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.exchange.CurrencyPair;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link Quote} aggregate override behavior (SPEC-0005): mandatory reason (BR6),
 * same-currency rule (BR7), immutable suggestion (BR5) and chained overrides.
 */
class QuoteAggregateTest {

  private static final Instant NOW = Instant.parse("2026-06-26T12:00:00Z");

  private static Quote composedQuote() {
    QuoteComposition composition =
        new QuoteComposition(
            PriceOrigin.MANUAL,
            Money.of(new BigDecimal("500.00"), "USD"),
            CurrencyPair.parse("USD/BRL"),
            new BigDecimal("5.400000"),
            UUID.randomUUID(),
            Money.of(new BigDecimal("2700.00"), "BRL"),
            new BigDecimal("0.15"),
            new BigDecimal("0.10"),
            Money.of(new BigDecimal("405.00"), "BRL"),
            Money.of(new BigDecimal("270.00"), "BRL"),
            Money.of(new BigDecimal("135.00"), "BRL"),
            false,
            BigDecimal.ZERO,
            Money.of(new BigDecimal("0.00"), "BRL"),
            "SYSTEM_DEFAULT",
            Money.of(new BigDecimal("2700.00"), "BRL"));
    return Quote.compose(UUID.randomUUID(), composition, null, NOW, "operador1");
  }

  @Test
  void chainsOverridesAndKeepsSuggestionImmutable() {
    Quote quote = composedQuote();

    quote.applyOverride(
        Money.of(new BigDecimal("2650.00"), "BRL"), "cliente recorrente", "op1", NOW);
    quote.applyOverride(Money.of(new BigDecimal("2600.00"), "BRL"), "fechamento", "op1", NOW);

    assertThat(quote.appliedAmount()).isEqualByComparingTo("2600.00");
    assertThat(quote.suggestedAmount()).isEqualByComparingTo("2700.00");
    assertThat(quote.overrides()).hasSize(2);
    assertThat(quote.overrides().get(0).fromAmount()).isEqualByComparingTo("2700.00");
    assertThat(quote.overrides().get(0).toAmount()).isEqualByComparingTo("2650.00");
    assertThat(quote.overrides().get(1).fromAmount()).isEqualByComparingTo("2650.00");
    assertThat(quote.overrides().get(1).toAmount()).isEqualByComparingTo("2600.00");
  }

  @Test
  void rejectsEmptyReason() {
    Quote quote = composedQuote();
    assertThatThrownBy(
            () -> quote.applyOverride(Money.of(new BigDecimal("2650.00"), "BRL"), "  ", "op1", NOW))
        .isInstanceOf(QuoteOverrideReasonRequiredException.class);
  }

  @Test
  void rejectsCurrencyMismatch() {
    Quote quote = composedQuote();
    assertThatThrownBy(
            () -> quote.applyOverride(Money.of(new BigDecimal("2650.00"), "USD"), "x", "op1", NOW))
        .isInstanceOf(QuoteOverrideCurrencyMismatchException.class);
  }

  @Test
  void composesIntegratedTrustingTheExternalPriceWithoutSuggestionEngine() {
    UUID accountId = UUID.randomUUID();
    UUID offerId = UUID.randomUUID();
    Quote quote =
        Quote.composeIntegrated(
            accountId, offerId, Money.of(new BigDecimal("480.00"), "BRL"), null, NOW, "connector");

    assertThat(quote.priceOrigin()).isEqualTo(PriceOrigin.INTEGRATED);
    assertThat(quote.status()).isEqualTo(QuoteStatus.COMPOSED);
    // suggested == applied == external price; no recomposition (BR2).
    assertThat(quote.suggestedAmount()).isEqualByComparingTo("480.00");
    assertThat(quote.appliedAmount()).isEqualByComparingTo("480.00");
    assertThat(quote.overrides()).isEmpty();
    // MANUAL-only composition fields stay null (DL-0018).
    assertThat(quote.fxRate()).isNull();
    assertThat(quote.baseConvertedAmount()).isNull();
    assertThat(quote.supplierCommission()).isNull();
    assertThat(quote.markupSource()).isNull();
    // The view exposes the trusted price and null MANUAL sections.
    assertThat(quote.toView().appliedAmount()).isEqualTo(Money.of(new BigDecimal("480.00"), "BRL"));
    assertThat(quote.toView().commission()).isNull();
    assertThat(quote.toView().markup()).isNull();
  }

  @Test
  void refusesOverrideOnIntegratedQuote() {
    Quote integrated =
        Quote.composeIntegrated(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Money.of(new BigDecimal("480.00"), "BRL"),
            null,
            NOW,
            "connector");

    assertThatThrownBy(
            () ->
                integrated.applyOverride(
                    Money.of(new BigDecimal("450.00"), "BRL"), "tentativa", "op1", NOW))
        .isInstanceOf(QuoteOverrideNotApplicableException.class);
    assertThat(integrated.appliedAmount()).isEqualByComparingTo("480.00");
    assertThat(integrated.overrides()).isEmpty();
  }
}
