package com.fksoft.infra.security;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A local ERP user for the self-hosted Authorization Server (SPEC-0024 — re-graduated in Phase 17,
 * ADR-0018/DL-0112). Phase 13 moved users to Keycloak and dropped the local store (V31); Phase 17
 * removes Keycloak and serves OIDC from the embedded Spring Authorization Server, so the user store
 * is re-created (V32) for the AS to authenticate against.
 *
 * <p><strong>Security (BR4, security.md):</strong> only the BCrypt password <em>hash</em> is stored
 * — never a plaintext password, token or secret. The granted roles are the {@code ROLE_*} names the
 * closed catalogue (DL-0082) defines; they feed the {@code realm_access.roles} claim (DL-0110).
 *
 * <p>Infra-only ({@code com.fksoft.infra.security}) — this is an IdP/authentication concern, not a
 * domain module; {@code domain.identity} keeps only the role/permission catalogue (the source of
 * truth of business authorization). Not a Modulith module.
 */
@Entity
@Table(name = "identity_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AppUser {

  @Id private UUID id;

  private String username;

  /** BCrypt hash ONLY — never a plaintext password (BR4). */
  private String passwordHash;

  private String displayName;

  /** ACTIVE | DISABLED. */
  private String status;

  private Instant createdAt;

  private long version;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role_name")
  private Set<String> roles = new LinkedHashSet<>();

  static AppUser of(
      String username, String passwordHash, String displayName, Set<String> roles, Instant now) {
    AppUser user = new AppUser();
    user.id = UUID.randomUUID();
    user.username = username;
    user.passwordHash = passwordHash;
    user.displayName = displayName;
    user.status = "ACTIVE";
    user.createdAt = now;
    user.version = 0L;
    user.roles = new LinkedHashSet<>(roles);
    return user;
  }

  boolean isActive() {
    return "ACTIVE".equals(status);
  }

  /** The granted role names (immutable copy). */
  Set<String> roleNames() {
    return Set.copyOf(roles);
  }
}
