package com.fksoft.infra.security;

/**
 * Minimal password policy for the local user store (SPEC-0024 — Fase 19c, DL-0125). Applied when a
 * password is set (the dev/E2E seeder today; a future user-management flow tomorrow) so a trivially
 * weak password never reaches the store. Kept deliberately small (Rule Zero): a minimum length and
 * a not-all-same-character rule — the real strength is BCrypt + the lockout, not a complexity
 * theatre. A pure function, so it is unit-tested directly.
 */
public final class PasswordPolicy {

  /** The minimum accepted password length. */
  public static final int MIN_LENGTH = 8;

  private PasswordPolicy() {}

  /**
   * Validates a raw password against the policy.
   *
   * @param rawPassword the plaintext password (never logged)
   * @throws WeakPasswordException when the password violates the policy
   */
  public static void validate(String rawPassword) {
    if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
      throw new WeakPasswordException("password must have at least " + MIN_LENGTH + " characters");
    }
    if (rawPassword.chars().distinct().count() == 1) {
      throw new WeakPasswordException("password must not be a single repeated character");
    }
  }

  /** Raised when a password violates {@link PasswordPolicy}. Carries no password value. */
  public static class WeakPasswordException extends RuntimeException {
    public WeakPasswordException(String message) {
      super(message);
    }
  }
}
