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
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A role and its named permissions (SPEC-0024 BR5/DL-0082) — the single source of truth of internal
 * authorization. Reference data, seeded by the V29 migration and read for the {@code GET
 * /api/identity/roles} catalogue. Module-internal.
 *
 * <p>The permissions are a small closed set kept as an {@code @ElementCollection} reflecting the
 * {@code role_permissions} table.
 */
@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class RoleEntity {

  @Id private String name;

  private String description;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_name"))
  @Column(name = "permission")
  private Set<String> permissions = new LinkedHashSet<>();

  /** The role name (e.g. {@code ROLE_DIRECTOR}). */
  public String name() {
    return name;
  }

  /** The granted permissions (immutable copy). */
  public Set<String> permissionNames() {
    return Set.copyOf(permissions);
  }

  /** Projects to the public read view. */
  public com.fksoft.domain.identity.RoleView toView() {
    return new com.fksoft.domain.identity.RoleView(name, description, permissionNames());
  }
}
