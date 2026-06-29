package com.fksoft.domain.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the {@link CurrencyPair} value object (SPEC-0003): format invariant and parsing.
 */
class CurrencyPairTest {

  @Test
  void parsesSlashAndDashSeparatorsToCanonicalText() {
    assertThat(CurrencyPair.parse("USD/BRL").asText()).isEqualTo("USD/BRL");
    assertThat(CurrencyPair.parse("usd-brl").asText()).isEqualTo("USD/BRL");
  }

  @ParameterizedTest
  @ValueSource(strings = {"USD", "US/BRL", "USD/BR", "USDD/BRL", "USD/BRLL", "USD//BRL", "12/BRL"})
  void rejectsMalformedPairs(String text) {
    assertThatThrownBy(() -> CurrencyPair.parse(text))
        .isInstanceOf(ExchangeCurrencyPairInvalidException.class);
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> CurrencyPair.parse(null))
        .isInstanceOf(ExchangeCurrencyPairInvalidException.class);
  }
}
