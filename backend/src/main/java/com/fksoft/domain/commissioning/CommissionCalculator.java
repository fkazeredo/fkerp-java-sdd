package com.fksoft.domain.commissioning;

/**
 * Public port of the commissioning module: computes a {@link CommissionStatement} from a {@link
 * CommissionInput}. Consumed in-process by Quoting (SPEC-0005) and by the preview endpoint. A port
 * (interface) is justified because another module depends on this capability.
 */
public interface CommissionCalculator {

  /**
   * Computes the two-sided commission and spread.
   *
   * @param input the commissionable base and the two percentages
   * @return the supplier/agent commissions, the spread and the negative-spread flag
   * @throws CommissionPctInvalidException when a percentage is outside {@code [0,1]} (BR2)
   * @throws CommissionBaseInvalidException when the base amount is negative (BR2)
   */
  CommissionStatement compute(CommissionInput input);
}
