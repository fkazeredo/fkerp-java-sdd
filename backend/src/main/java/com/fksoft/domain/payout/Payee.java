package com.fksoft.domain.payout;

/**
 * The party a payout pays (SPEC-0017 BR1): an external id (value reference, never an FK) plus its
 * {@link PayeeType}.
 *
 * @param id the payee's external id (value)
 * @param type the payee kind
 */
public record Payee(String id, PayeeType type) {

  public Payee {
    if (id == null || id.isBlank() || type == null) {
      throw new PayoutPayeeInvalidException();
    }
    id = id.trim();
  }
}
