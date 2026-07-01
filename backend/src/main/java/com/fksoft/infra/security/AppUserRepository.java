package com.fksoft.infra.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the local {@link AppUser} store the self-hosted Authorization Server authenticates
 * against (SPEC-0024 Phase 17 / DL-0112). Infra-only.
 */
interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  /** The user with this username, if any (the login lookup). */
  Optional<AppUser> findByUsername(String username);

  /** Whether a user with this username already exists (guards the idempotent dev seed). */
  boolean existsByUsername(String username);
}
