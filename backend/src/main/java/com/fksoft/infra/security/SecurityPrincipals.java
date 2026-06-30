package com.fksoft.infra.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

/** Small helper to read the stable user id from an {@link Authentication} (SPEC-0024/DL-0079). */
final class SecurityPrincipals {

  static final String USER_ID_CLAIM = "uid";
  static final String USERNAME_CLAIM = "preferred_username";
  static final String ROLES_CLAIM = "roles";

  private SecurityPrincipals() {}

  /**
   * The stable user id: the {@code uid} claim of the JWT principal when present, otherwise the
   * authentication name (e.g. the test/dev actor that is not token-backed).
   */
  static String userId(Authentication authentication) {
    if (authentication.getPrincipal() instanceof Jwt jwt) {
      String uid = jwt.getClaimAsString(USER_ID_CLAIM);
      if (uid != null && !uid.isBlank()) {
        return uid;
      }
    }
    return authentication.getName();
  }
}
