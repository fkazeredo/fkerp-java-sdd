package com.fksoft.infra.security;

import java.util.Set;

/**
 * Immutable snapshot of the current authenticated principal (ADR 0011/0012). Controllers and
 * application services obtain it through {@link UserContextProvider} instead of touching {@code
 * SecurityContextHolder} directly.
 *
 * @param userId stable identifier of the user
 * @param username human-readable login/name
 * @param roles granted roles/authorities
 */
public record UserContext(String userId, String username, Set<String> roles) {

  public UserContext {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }
}
