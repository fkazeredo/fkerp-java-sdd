package com.fksoft.domain.billing;

import com.fksoft.domain.money.Money;

/**
 * Strategy port that computes the taxes of a commission invoice for a given regime (SPEC-0016 BR2;
 * DL-0044). The regime (Simples/Presumido/Real) is parametrized behind this port so the
 * accountant's real regime can be plugged later without refactoring the aggregate or the issuance
 * flow. The taxable base is the commission (BR1) — implementations MUST NOT apply taxes to anything
 * else.
 */
public interface TaxRegimeStrategy {

  /**
   * Assesses the ISS and any withholdings over the commission base.
   *
   * @param commissionBase the commission (the taxable base, never the gross package — BR1)
   * @param municipality the IBGE municipality code of incidence (drives the ISS rate)
   * @param serviceCode the municipal service code (may refine the rate; reserved)
   * @return the tax assessment (ISS + withholdings + regime)
   */
  TaxAssessment assess(Money commissionBase, String municipality, String serviceCode);
}
