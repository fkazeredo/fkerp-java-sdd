package com.fksoft.application.api.dto;

import com.fksoft.infra.security.UserContext;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The current user (SPEC-0024 — {@code GET /api/identity/me}): id, username and roles resolved from
 * the verified IdP token. Carries no secret (BR4). Only the {@code ROLE_*} authorities are surfaced
 * as {@code roles} (the {@code SCOPE_*} authorities the resource server also derives are not part
 * of this read contract — DL-0104).
 *
 * @param userId stable user id (the IdP subject)
 * @param username the login (preferred_username)
 * @param displayName the human-readable name (may be {@code null} when resolved from a bare token)
 * @param roles the granted role names (sorted for a stable contract)
 */
public record MeResponse(String userId, String username, String displayName, Set<String> roles) {

  /** From the security context (resolved from the verified token). */
  public static MeResponse from(UserContext context) {
    Set<String> roles =
        context.roles().stream()
            .filter(authority -> authority.startsWith("ROLE_"))
            .collect(Collectors.toCollection(TreeSet::new));
    return new MeResponse(context.userId(), context.username(), null, roles);
  }
}
