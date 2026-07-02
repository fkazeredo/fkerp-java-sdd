package com.fksoft.domain.payout;

/**
 * The party a payout pays (SPEC-0017 BR1): an external id (value reference, never an FK) plus its
 * payee-type cadastro code (was {@code PayeeType}; SPEC-0031/DL-0118).
 *
 * @param id the payee's external id (value)
 * @param type the payee kind (payee-type cadastro code)
 */
public record Payee(String id, String type) {

  public Payee {
    if (id == null || id.isBlank() || type == null || type.isBlank()) {
      throw new PayoutPayeeInvalidException();
    }
    id = id.trim();
    type = type.trim();
  }
}
