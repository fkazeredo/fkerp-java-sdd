package com.fksoft.domain.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the {@link Document} value object (SPEC-0002 BR2): valid check digits accepted,
 * invalid check digits and repeated-digit documents rejected, and length enforced per legal type.
 */
class DocumentTest {

  @Test
  void acceptsValidCnpjAndNormalizesPunctuation() {
    Document fromDigits = Document.of(LegalType.CNPJ, "12345678000195");
    Document fromPunctuated = Document.of(LegalType.CNPJ, "12.345.678/0001-95");

    assertThat(fromDigits.number()).isEqualTo("12345678000195");
    assertThat(fromPunctuated.number()).isEqualTo("12345678000195");
    assertThat(fromDigits.legalType()).isEqualTo(LegalType.CNPJ);
  }

  @Test
  void acceptsValidCpf() {
    Document document = Document.of(LegalType.CPF, "529.982.247-25");

    assertThat(document.number()).isEqualTo("52998224725");
    assertThat(document.legalType()).isEqualTo(LegalType.CPF);
  }

  @Test
  void acceptsMeiAsFourteenDigitCnpj() {
    assertThatCode(() -> Document.of(LegalType.MEI, "12345678000195")).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345678000196", "12345678000100", "00000000000000", "11111111111111"})
  void rejectsInvalidCnpj(String number) {
    assertThatThrownBy(() -> Document.of(LegalType.CNPJ, number))
        .isInstanceOf(AccountDocumentInvalidException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"52998224726", "12345678901", "00000000000", "11111111111"})
  void rejectsInvalidCpf(String number) {
    assertThatThrownBy(() -> Document.of(LegalType.CPF, number))
        .isInstanceOf(AccountDocumentInvalidException.class);
  }

  @Test
  void rejectsWrongLengthForLegalType() {
    assertThatThrownBy(() -> Document.of(LegalType.CPF, "12345678000195"))
        .isInstanceOf(AccountDocumentInvalidException.class);
    assertThatThrownBy(() -> Document.of(LegalType.CNPJ, "52998224725"))
        .isInstanceOf(AccountDocumentInvalidException.class);
  }

  @Test
  void rejectsNullOrEmpty() {
    assertThatThrownBy(() -> Document.of(LegalType.CNPJ, null))
        .isInstanceOf(AccountDocumentInvalidException.class);
    assertThatThrownBy(() -> Document.of(LegalType.CNPJ, "   "))
        .isInstanceOf(AccountDocumentInvalidException.class);
  }
}
