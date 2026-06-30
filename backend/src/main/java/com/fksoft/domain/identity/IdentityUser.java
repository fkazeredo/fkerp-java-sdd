package com.fksoft.domain.identity;

import com.fksoft.domain.ModuleInternal;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Local internal-user aggregate (SPEC-0024 BR8/DL-0080). The minimal store the in-house JWT issuer
 * authenticates against: a unique {@code username}, the <strong>BCrypt password hash</strong>
 * (never a plaintext password/token — BR4), a display name, a status and the granted roles (by
 * value). Users migrate to the external IdP in Phase 13; until then this is the source of the
 * login. Module-internal.
 *
 * <p>The roles are a small set of role names kept as an {@code @ElementCollection} (the shape is
 * tiny and known — Rule Zero), reflecting the {@code user_roles} table.
 */
@Entity
@Table(name = "identity_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class IdentityUser {

  @Id private UUID id;

  private String username;

  private String passwordHash;

  private String displayName;

  private String status;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role_name")
  private Set<String> roles = new LinkedHashSet<>();

  private Instant createdAt;

  @Version private Long version;

  /**
   * Creates a new ACTIVE user with an already-encoded password hash.
   *
   * @param username the unique login (required)
   * @param passwordHash the BCrypt hash (already encoded — never a plaintext)
   * @param displayName the human-readable name
   * @param roles the granted role names
   * @param now the creation instant (UTC)
   * @return a new, persistable ACTIVE user
   */
  public static IdentityUser create(
      String username, String passwordHash, String displayName, Set<String> roles, Instant now) {
    IdentityUser user = new IdentityUser();
    user.id = UUID.randomUUID();
    user.username = username;
    user.passwordHash = passwordHash;
    user.displayName = displayName;
    user.status = "ACTIVE";
    user.roles = roles == null ? new LinkedHashSet<>() : new LinkedHashSet<>(roles);
    user.createdAt = now;
    return user;
  }

  /** Whether the account is active (a disabled account must not authenticate). */
  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  /** The user id. */
  public UUID id() {
    return id;
  }

  /** The login. */
  public String username() {
    return username;
  }

  /** The BCrypt password hash (used only by the password encoder for matching — never exposed). */
  public String passwordHash() {
    return passwordHash;
  }

  /** The human-readable display name. */
  public String displayName() {
    return displayName;
  }

  /** The granted role names (immutable copy). */
  public Set<String> roleNames() {
    return Set.copyOf(roles);
  }
}
