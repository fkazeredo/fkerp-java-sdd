package com.fksoft.application.api.dto;

import com.fksoft.domain.identity.AuthenticatedUser;
import com.fksoft.infra.security.UserContext;
import java.util.Set;
import java.util.TreeSet;

/**
 * The current user (SPEC-0024 — {@code GET /api/identity/me}) and the {@code user} block of the
 * login response: id, username and roles resolved from the token. Carries no secret (BR4).
 *
 * @param userId stable user id
 * @param username the login
 * @param displayName the human-readable name (may be {@code null} when resolved from a bare token)
 * @param roles the granted role names (sorted for a stable contract)
 */
public record MeResponse(String userId, String username, String displayName, Set<String> roles) {

  /** From a freshly authenticated user (login). */
  public static MeResponse from(AuthenticatedUser user) {
    return new MeResponse(
        user.userId().toString(), user.username(), user.displayName(), new TreeSet<>(user.roles()));
  }

  /** From the security context (subsequent {@code /me} calls resolve from the verified token). */
  public static MeResponse from(UserContext context) {
    return new MeResponse(
        context.userId(), context.username(), null, new TreeSet<>(context.roles()));
  }
}
