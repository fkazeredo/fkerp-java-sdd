package com.fksoft.domain.commissioning;

import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Pure two-sided commission calculation (SPEC-0004), implementing the {@link CommissionCalculator}
 * port. Stateless: no persistence, no events (accrual/reversal happen on booking confirm/cancel —
 * other slices). A negative spread is exposed, never blocked (BR3).
 */
@Service
public class CommissionService implements CommissionCalculator {

  @Override
  public CommissionStatement compute(CommissionInput input) {
    Money base = input.commissionableBase();
    if (base == null || base.isNegative()) {
      throw new CommissionBaseInvalidException();
    }
    BigDecimal supplierPct = requireRate(input.supplierCommissionPct(), "supplierCommissionPct");
    BigDecimal agentPct = requireRate(input.agentCommissionPct(), "agentCommissionPct");

    Money supplierCommission = base.multiply(supplierPct);
    Money agentCommission = base.multiply(agentPct);
    Money spread = supplierCommission.subtract(agentCommission);
    return new CommissionStatement(
        supplierCommission, agentCommission, spread, spread.isNegative());
  }

  private static BigDecimal requireRate(BigDecimal pct, String field) {
    if (pct == null || pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(BigDecimal.ONE) > 0) {
      throw new CommissionPctInvalidException(field);
    }
    return pct;
  }
}
