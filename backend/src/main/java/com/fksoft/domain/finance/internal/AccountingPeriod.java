package com.fksoft.domain.finance.internal;

import com.fksoft.domain.finance.PeriodStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Accounting period aggregate (SPEC-0015 BR3): the monthly close unit, identified by its {@code
 * YYYY-MM} string and governed by an explicit state machine (OPEN→CLOSING→CLOSED). A period is
 * created lazily OPEN the first time it is referenced. The close transition is driven by {@code
 * FinanceService} after the Compliance veto passes. Module-internal.
 */
@Entity
@Table(name = "accounting_periods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountingPeriod {

  @Id private String period;

  @Enumerated(EnumType.STRING)
  private PeriodStatus status;

  private Instant closedAt;
  private String closedBy;

  @Version private Long version;

  /** Creates a new OPEN period for the given {@code YYYY-MM} value. */
  public static AccountingPeriod open(String period) {
    AccountingPeriod accountingPeriod = new AccountingPeriod();
    accountingPeriod.period = period;
    accountingPeriod.status = PeriodStatus.OPEN;
    return accountingPeriod;
  }

  /** Whether the period is sealed (CLOSED) and rejects new entries (BR4). */
  public boolean isClosed() {
    return status == PeriodStatus.CLOSED;
  }

  /** Marks the period CLOSING while the Compliance veto is evaluated (BR3). */
  public void beginClosing() {
    status = PeriodStatus.CLOSING;
  }

  /** Reopens to OPEN after a vetoed close attempt (the close did not complete). */
  public void abortClosing() {
    status = PeriodStatus.OPEN;
  }

  /** Seals the period CLOSED once the veto passed (BR3). */
  public void close(Instant now, String actor) {
    status = PeriodStatus.CLOSED;
    closedAt = now;
    closedBy = actor;
  }

  /** The period id ({@code YYYY-MM}). */
  public String period() {
    return period;
  }
}
