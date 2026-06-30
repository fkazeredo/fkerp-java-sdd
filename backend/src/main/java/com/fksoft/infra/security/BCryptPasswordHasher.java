package com.fksoft.infra.security;

import com.fksoft.domain.identity.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt adapter for the domain {@link PasswordHasher} port (SPEC-0024/DL-0080). Delegates to the
 * Spring Security {@link PasswordEncoder} bean (configured BCrypt). Keeps the concrete crypto
 * library out of the domain (ADR 0010/0012); the raw password is never stored or logged (BR4).
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

  private final PasswordEncoder passwordEncoder;

  public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public String hash(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String storedHash) {
    return passwordEncoder.matches(rawPassword, storedHash);
  }
}
