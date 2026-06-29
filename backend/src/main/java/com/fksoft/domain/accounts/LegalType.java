package com.fksoft.domain.accounts;

/**
 * Legal type of a commercial account (BR1). Determines the structural shape of the document (BR2):
 * {@link #CNPJ} and {@link #MEI} are 14-digit company registrations, {@link #CPF} is an 11-digit
 * natural-person registration. External (API/persistence) value is the constant name.
 */
public enum LegalType {

  /** Brazilian company taxpayer registry (14 digits). */
  CNPJ(14),

  /** Individual micro-entrepreneur — holds a 14-digit CNPJ. */
  MEI(14),

  /** Brazilian natural-person taxpayer registry (11 digits). */
  CPF(11);

  private final int digitCount;

  LegalType(int digitCount) {
    this.digitCount = digitCount;
  }

  /** Number of digits a valid document of this legal type must have. */
  public int digitCount() {
    return digitCount;
  }
}
