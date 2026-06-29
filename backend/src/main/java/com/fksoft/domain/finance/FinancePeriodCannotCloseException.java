package com.fksoft.domain.finance;

import com.fksoft.domain.error.DomainException;
import com.fksoft.domain.error.ErrorDetails;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raised when {@code closePeriod} is vetoed by the Compliance because the period has non-conformant
 * entries (SPEC-0015 BR3; SPEC-0008 BR6). Mapped to {@code 409 Conflict}. The blocking entries are
 * exposed as {@link ErrorDetails} so the response lists what is missing (one field per pending
 * entry: {@code entryId -> "TYPE missing [DOC,...]"}).
 */
public class FinancePeriodCannotCloseException extends DomainException implements ErrorDetails {

  private final transient List<PendingEntry> pending;

  public FinancePeriodCannotCloseException(List<PendingEntry> pending) {
    super("finance.period.cannot-close");
    this.pending = pending == null ? List.of() : List.copyOf(pending);
  }

  /** The blocking entries that prevented the close. */
  public List<PendingEntry> pending() {
    return List.copyOf(pending);
  }

  @Override
  public Map<String, Object> details() {
    Map<String, Object> details = new LinkedHashMap<>();
    for (PendingEntry entry : pending) {
      details.put(
          String.valueOf(entry.entryId()), entry.entryType() + " missing " + entry.missing());
    }
    return details;
  }
}
