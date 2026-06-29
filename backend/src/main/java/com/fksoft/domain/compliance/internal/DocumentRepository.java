package com.fksoft.domain.compliance.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Document}. Module-internal: only the Compliance module uses it. */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

  /** Documents whose retention deadline is on or before the cutoff (retention-expiring job). */
  List<Document> findByRetentionUntilLessThanEqual(LocalDate cutoff);
}
