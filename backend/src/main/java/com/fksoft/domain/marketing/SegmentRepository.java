package com.fksoft.domain.marketing;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command repository for the {@link Segment} aggregate (SPEC-0019). Module-internal: other modules
 * never touch it (Spring Modulith).
 */
@ModuleInternal
public interface SegmentRepository extends JpaRepository<Segment, UUID> {}
