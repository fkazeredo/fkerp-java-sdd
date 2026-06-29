package com.fksoft.domain.finance;

import java.util.List;
import java.util.UUID;

/**
 * A ledger entry that blocks the monthly close because a mandatory document is missing (SPEC-0015
 * BR3; SPEC-0008 BR6). It is the unit the close-check returns and the cannot-close error lists.
 *
 * @param entryId the non-conformant ledger entry id
 * @param entryType the entry's business type (value)
 * @param missing the document types still missing for this entry
 */
public record PendingEntry(UUID entryId, String entryType, List<String> missing) {

  public PendingEntry {
    missing = missing == null ? List.of() : List.copyOf(missing);
  }
}
