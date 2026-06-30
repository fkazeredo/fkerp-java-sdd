package com.fksoft.domain.identity;

import java.util.Set;

/**
 * Public read view of a role and its permissions (SPEC-0024 — {@code GET /api/identity/roles}).
 *
 * @param name the role name (e.g. {@code ROLE_DIRECTOR})
 * @param description the human-readable description
 * @param permissions the granted named permissions
 */
public record RoleView(String name, String description, Set<String> permissions) {

  public RoleView {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }
}
