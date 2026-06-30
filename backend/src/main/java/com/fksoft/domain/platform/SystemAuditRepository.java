package com.fksoft.domain.platform;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Module-internal repository for the append-only system audit (SPEC-0023 BR4). Reads are filterable
 * by actor, type and time window via a {@link org.springframework.data.jpa.domain.Specification}
 * (which produces typed criteria, avoiding Postgres' untyped-null-parameter issue), paginated,
 * newest first. There is intentionally no update/delete method — the trail is append-only. Only the
 * {@code platform} domain reaches it (Spring Modulith).
 */
@ModuleInternal
public interface SystemAuditRepository
    extends JpaRepository<SystemAuditEntry, UUID>, JpaSpecificationExecutor<SystemAuditEntry> {}
