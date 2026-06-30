package com.fksoft.domain.identity;

import com.fksoft.domain.ModuleInternal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Module-internal repository for the local user store (SPEC-0024/DL-0080). Lookups are by username
 * (login) and id. Only the {@code identity} domain reaches it (Spring Modulith).
 */
@ModuleInternal
public interface IdentityUserRepository extends JpaRepository<IdentityUser, UUID> {

  /** Finds an active-or-not user by its unique login. */
  Optional<IdentityUser> findByUsername(String username);

  /**
   * Whether a user with this login already exists (used by the dev/test seeder for idempotency).
   */
  boolean existsByUsername(String username);
}
