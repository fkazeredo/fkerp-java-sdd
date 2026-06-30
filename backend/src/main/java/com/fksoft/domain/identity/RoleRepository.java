package com.fksoft.domain.identity;

import com.fksoft.domain.ModuleInternal;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Module-internal repository for the role/permission reference data (SPEC-0024/DL-0082). Read-only
 * in practice — roles are seeded by V29. Only the {@code identity} domain reaches it (Spring
 * Modulith).
 */
@ModuleInternal
public interface RoleRepository extends JpaRepository<RoleEntity, String> {}
