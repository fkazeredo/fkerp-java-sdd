package com.fksoft.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.infra.security.PasswordPolicy;
import org.junit.jupiter.api.Test;

/** Unit tests for the minimal password policy (SPEC-0024 Fase 19c, DL-0125). */
class PasswordPolicyTest {

  @Test
  void acceptsAPasswordMeetingTheMinimum() {
    assertThatCode(() -> PasswordPolicy.validate("dev12345")).doesNotThrowAnyException();
  }

  @Test
  void rejectsATooShortPassword() {
    assertThatThrownBy(() -> PasswordPolicy.validate("short"))
        .isInstanceOf(PasswordPolicy.WeakPasswordException.class);
  }

  @Test
  void rejectsANullPassword() {
    assertThatThrownBy(() -> PasswordPolicy.validate(null))
        .isInstanceOf(PasswordPolicy.WeakPasswordException.class);
  }

  @Test
  void rejectsASingleRepeatedCharacter() {
    assertThatThrownBy(() -> PasswordPolicy.validate("aaaaaaaa"))
        .isInstanceOf(PasswordPolicy.WeakPasswordException.class);
  }
}
