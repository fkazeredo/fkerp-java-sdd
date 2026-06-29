package com.fksoft.domain.compliance.internal;

import com.fksoft.domain.compliance.CloseCheckView;
import com.fksoft.domain.compliance.ComplianceService;
import com.fksoft.domain.finance.AccountingPeriodId;
import com.fksoft.domain.finance.CloseDecision;
import com.fksoft.domain.finance.CloseGuard;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The real {@link CloseGuard} (SPEC-0008 BR6): the Compliance answers the Finance close veto by
 * running the period close-check. Marked {@code @Primary} so it supersedes the Finance default
 * ({@code AlwaysAllowsCloseGuard}) for injection, fulfilling the seam without changing Finance.
 * Module-internal: it is wired into Finance only through the public {@code CloseGuard} port.
 *
 * <p>Finance ({@code FinanceService}) collaborates with Compliance two ways at runtime — Compliance
 * reads the ledger ({@code LedgerDirectory}) and Finance consults this guard — which is a benign DI
 * cycle. It is broken with {@code @Lazy} on the {@link ComplianceService} dependency here, so the
 * guard holds a lazy proxy and nothing is eagerly circular at startup.
 */
@Component
@Primary
class ComplianceCloseGuard implements CloseGuard {

  private final ComplianceService complianceService;

  ComplianceCloseGuard(@Lazy ComplianceService complianceService) {
    this.complianceService = complianceService;
  }

  @Override
  public CloseDecision checkClose(AccountingPeriodId period) {
    CloseCheckView check = complianceService.closeCheck(period.value());
    return new CloseDecision(check.canClose(), check.pending());
  }
}
