package com.fksoft.domain.identity;

/**
 * Domain port for password hashing/verification (SPEC-0024/DL-0080). Keeps the domain free of the
 * concrete crypto library (Spring Security's BCrypt lives in {@code infra.security}, ADR
 * 0010/0012): the {@link IdentityService} hashes/matches through this port, never a plaintext is
 * stored or logged (BR4).
 */
public interface PasswordHasher {

  /** Encodes a raw password into a storable hash (BCrypt). */
  String hash(String rawPassword);

  /** Whether the raw password matches the stored hash (constant-time comparison by the encoder). */
  boolean matches(String rawPassword, String storedHash);
}
