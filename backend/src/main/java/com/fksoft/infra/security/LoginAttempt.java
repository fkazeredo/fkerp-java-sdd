package com.fksoft.infra.security;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Consecutive failed-login counter for a username (SPEC-0024 — Fase 19c, DL-0125). It backs the
 * brute-force lockout of the self-hosted Authorization Server's form login. Only the counter and
 * the lock window are stored — never a password or secret (BR4). Infra-only (an authentication
 * concern), not a Modulith domain module.
 */
@Entity
@Table(name = "login_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class LoginAttempt {

  @Id private String username;

  private int failedCount;

  private Instant lockedUntil;

  private Instant updatedAt;

  static LoginAttempt of(String username, Instant now) {
    LoginAttempt attempt = new LoginAttempt();
    attempt.username = username;
    attempt.failedCount = 0;
    attempt.updatedAt = now;
    return attempt;
  }

  /** Whether the account is currently locked at {@code now}. */
  boolean isLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  /**
   * Records a failed attempt. When the running count reaches {@code maxAttempts} the account is
   * locked for {@code lockSeconds} and the counter resets, so the next window starts clean.
   *
   * @return {@code true} when this failure triggered a lock (crossed the threshold)
   */
  boolean recordFailure(int maxAttempts, long lockSeconds, Instant now) {
    this.failedCount += 1;
    this.updatedAt = now;
    if (this.failedCount >= maxAttempts) {
      this.lockedUntil = now.plusSeconds(lockSeconds);
      this.failedCount = 0;
      return true;
    }
    return false;
  }

  /** Clears the counter and any lock after a successful login. */
  void recordSuccess(Instant now) {
    this.failedCount = 0;
    this.lockedUntil = null;
    this.updatedAt = now;
  }
}
