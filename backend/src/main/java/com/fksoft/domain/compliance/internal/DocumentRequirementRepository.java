package com.fksoft.domain.compliance.internal;

import com.fksoft.domain.compliance.RequirementPhase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the seeded {@link DocumentRequirement} policy rows. Module-internal, read-only.
 */
public interface DocumentRequirementRepository
    extends JpaRepository<DocumentRequirement, DocumentRequirement.Key> {

  /** The requirements for an entry type in a given phase (the close-check uses AT_REGISTRATION). */
  List<DocumentRequirement> findByEntryTypeAndPhase(String entryType, RequirementPhase phase);
}
