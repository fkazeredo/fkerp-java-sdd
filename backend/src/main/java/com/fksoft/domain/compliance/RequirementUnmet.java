package com.fksoft.domain.compliance;

import com.fksoft.domain.finance.PendingEntry;
import java.time.Instant;
import java.util.List;

/**
 * Business fact: a close was checked and the period has non-conformant entries (SPEC-0008 BR6).
 * Published in-process; consumed by Intelligence (8.2-H: what is missing to close the month).
 *
 * @param period the period checked ({@code YYYY-MM})
 * @param pendingEntries the entries still missing a mandatory document
 * @param occurredAt when the check ran
 */
public record RequirementUnmet(
    String period, List<PendingEntry> pendingEntries, Instant occurredAt) {

  public RequirementUnmet {
    pendingEntries = pendingEntries == null ? List.of() : List.copyOf(pendingEntries);
  }
}
