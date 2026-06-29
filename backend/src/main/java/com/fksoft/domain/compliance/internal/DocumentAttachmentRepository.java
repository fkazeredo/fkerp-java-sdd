package com.fksoft.domain.compliance.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link DocumentAttachment}. Module-internal. */
public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachment, UUID> {

  /** Whether this document is already attached to this entry (idempotent attach, BR5). */
  boolean existsByDocumentIdAndEntryId(UUID documentId, UUID entryId);

  /** The attachments for a set of entries (used by the close-check to find covered entries). */
  List<DocumentAttachment> findByEntryIdIn(List<UUID> entryIds);
}
