package com.fksoft.domain.marketing.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Command repository for the {@link Segment} aggregate (SPEC-0019). Module-internal: other modules
 * never touch it (Spring Modulith).
 */
public interface SegmentRepository extends JpaRepository<Segment, UUID> {}
