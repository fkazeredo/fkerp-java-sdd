package com.fksoft.infra.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** Small helper to read the stable user id from an {@link Authentication} (SPEC-0024/DL-0104). */
final class SecurityPrincipals {

  static final String USERNAME_CLAIM = "preferred_username";

  private SecurityPrincipals() {}

  /**
   * The stable user id: the JWT {@code sub} claim (the IdP's subject — stable per user in Keycloak)
   * when the principal is token-backed, otherwise the authentication name (e.g. the test/dev actor
   * that is not token-backed).
   */
  static String userId(Authentication authentication) {
    if (authentication.getPrincipal() instanceof Jwt jwt) {
      String sub = jwt.getSubject();
      if (sub != null && !sub.isBlank()) {
        return sub;
      }
    }
    return authentication.getName();
  }
}
