package com.fksoft.domain.intelligence.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-agency running totals the {@code PromoFxAdvisor} reads (DL-0034/DL-0035): the accrued
 * subsidy, the realized gap and the volume of closed positions attributed to one account, all
 * derived from consumed events. It is a recomputable projection — never a command into another
 * module. Module-internal.
 */
@Entity
@Table(name = "intelligence_agency_fx_accrual")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgencyFxAccrual {

  @Id private UUID accountId;

  private BigDecimal accruedSubsidyBrl;
  private BigDecimal realizedGapBrl;
  private long volumeAttracted;

  private Instant createdAt;
  private Instant updatedAt;

  @Version private Long version;

  /** Creates a zeroed accrual for an account. */
  public static AgencyFxAccrual forAccount(UUID accountId, Instant now) {
    AgencyFxAccrual accrual = new AgencyFxAccrual();
    accrual.accountId = accountId;
    accrual.accruedSubsidyBrl = BigDecimal.ZERO;
    accrual.realizedGapBrl = BigDecimal.ZERO;
    accrual.volumeAttracted = 0L;
    accrual.createdAt = now;
    accrual.updatedAt = now;
    return accrual;
  }

  /**
   * Rolls one booking's buffered facts into the agency totals. {@code volumeAttracted} counts the
   * booking only when its FX position has closed (a realized, attributable outcome).
   */
  public void roll(BigDecimal subsidyBrl, BigDecimal gapBrl, boolean positionClosed, Instant now) {
    this.accruedSubsidyBrl = this.accruedSubsidyBrl.add(subsidyBrl);
    this.realizedGapBrl = this.realizedGapBrl.add(gapBrl);
    if (positionClosed) {
      this.volumeAttracted += 1;
    }
    this.updatedAt = now;
  }
}
