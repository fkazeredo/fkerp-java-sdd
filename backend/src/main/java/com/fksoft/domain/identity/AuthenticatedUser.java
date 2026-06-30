package com.fksoft.domain.identity;

import java.util.Set;
import java.util.UUID;

/**
 * The result of a successful authentication (SPEC-0024 BR1): the verified principal whose roles a
 * token will carry. The delivery layer mints the JWT from this; the {@code UserContextProvider}
 * resolves the same shape from the verified token on subsequent requests.
 *
 * @param userId stable user id
 * @param username the login
 * @param displayName the human-readable name
 * @param roles the granted role names
 */
public record AuthenticatedUser(
    UUID userId, String username, String displayName, Set<String> roles) {

  public AuthenticatedUser {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }
}
