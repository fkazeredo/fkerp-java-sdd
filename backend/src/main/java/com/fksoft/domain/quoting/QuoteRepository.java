package com.fksoft.domain.quoting;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Aggregate repository for {@link Quote} (and its cascaded override records). Module-internal. The
 * override collection is loaded lazily within the service transaction when a view/snapshot is
 * built.
 */
@ModuleInternal
public interface QuoteRepository extends JpaRepository<Quote, UUID> {}
