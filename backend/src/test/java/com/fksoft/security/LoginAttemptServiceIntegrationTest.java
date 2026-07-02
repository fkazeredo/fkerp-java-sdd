package com.fksoft.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.infra.security.LoginAttemptService;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the brute-force login lockout (SPEC-0024 Fase 19c, DL-0125) against real
 * Postgres. The default policy is 5 attempts / 900s. It proves: a fresh user is not locked; the
 * account locks on the 5th consecutive failure; a success clears the counter; and the lock is per
 * username.
 */
class LoginAttemptServiceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private LoginAttemptService loginAttempts;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM login_attempts");
  }

  @Test
  void aFreshUserIsNotLocked() {
    assertThat(loginAttempts.isLocked("nobody")).isFalse();
  }

  @Test
  void locksAfterTheFifthConsecutiveFailure() {
    for (int i = 1; i <= 4; i++) {
      boolean lockedNow = loginAttempts.onFailure("brute");
      assertThat(lockedNow).as("attempt %d must not lock yet", i).isFalse();
      assertThat(loginAttempts.isLocked("brute")).isFalse();
    }
    boolean lockedOnFifth = loginAttempts.onFailure("brute");
    assertThat(lockedOnFifth).isTrue();
    assertThat(loginAttempts.isLocked("brute")).isTrue();
  }

  @Test
  void aSuccessClearsTheCounterAndTheLock() {
    for (int i = 1; i <= 5; i++) {
      loginAttempts.onFailure("recover");
    }
    assertThat(loginAttempts.isLocked("recover")).isTrue();

    loginAttempts.onSuccess("recover");
    assertThat(loginAttempts.isLocked("recover")).isFalse();
  }

  @Test
  void theLockIsPerUsername() {
    for (int i = 1; i <= 5; i++) {
      loginAttempts.onFailure("locked-one");
    }
    assertThat(loginAttempts.isLocked("locked-one")).isTrue();
    assertThat(loginAttempts.isLocked("other-user")).isFalse();
  }
}
