package com.fksoft.infra.security;

import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brute-force lockout for the self-hosted Authorization Server's form login (SPEC-0024 — Fase 19c,
 * DL-0125). After {@code security.login.max-attempts} consecutive failures a username is locked for
 * {@code security.login.lock-seconds}; a success clears the counter. The check runs inside {@link
 * AppUserDetailsService} (the account is presented as locked) and the failure/success hooks are
 * wired to the form login. No password/secret is ever logged (BR4) — only the username and the
 * outcome.
 */
@Slf4j
@Service
public class LoginAttemptService {

  private final LoginAttemptRepository repository;
  private final Clock clock;
  private final int maxAttempts;
  private final long lockSeconds;

  public LoginAttemptService(
      LoginAttemptRepository repository,
      Clock clock,
      @Value("${security.login.max-attempts:5}") int maxAttempts,
      @Value("${security.login.lock-seconds:900}") long lockSeconds) {
    this.repository = repository;
    this.clock = clock;
    this.maxAttempts = maxAttempts;
    this.lockSeconds = lockSeconds;
  }

  /** Whether the username is currently locked out. */
  @Transactional(readOnly = true)
  public boolean isLocked(String username) {
    return repository
        .findByUsername(username)
        .map(attempt -> attempt.isLocked(clock.instant()))
        .orElse(false);
  }

  /**
   * Records a failed login. Returns {@code true} when this failure crossed the threshold and locked
   * the account — the caller audits that transition (AUTH_LOCKOUT).
   */
  @Transactional
  public boolean onFailure(String username) {
    LoginAttempt attempt =
        repository
            .findByUsername(username)
            .orElseGet(() -> LoginAttempt.of(username, clock.instant()));
    boolean lockedNow = attempt.recordFailure(maxAttempts, lockSeconds, clock.instant());
    repository.save(attempt);
    if (lockedNow) {
      log.warn("login.lockout username={} lockedForSeconds={}", username, lockSeconds);
    }
    return lockedNow;
  }

  /** Clears the counter after a successful login. */
  @Transactional
  public void onSuccess(String username) {
    repository
        .findByUsername(username)
        .ifPresent(
            attempt -> {
              attempt.recordSuccess(clock.instant());
              repository.save(attempt);
            });
  }
}
