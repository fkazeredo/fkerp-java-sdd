package com.fksoft.domain.billing;

/**
 * Kind of tax withholding that may apply to the commission depending on the taker/regime (SPEC-0016
 * BR2). Under Simples Nacional (the v1 default, DL-0044) none of the federal withholdings apply;
 * they are populated by the strategy of a Presumido/Real regime when the accountant confirms it.
 */
public enum WithholdingKind {
  IRRF,
  PIS,
  COFINS,
  CSLL,
  ISS_RETIDO
}
