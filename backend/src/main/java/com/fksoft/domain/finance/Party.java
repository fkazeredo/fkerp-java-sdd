package com.fksoft.domain.finance;

/**
 * The counterparty of a ledger entry (SPEC-0015 BR1): an opaque id plus its party-type cadastro
 * code (was {@code PartyType}; SPEC-0031/DL-0118). It is a value — a reference to another context
 * (Accounts/Suppliers) by id, never a cross-module FK.
 *
 * @param id the counterparty id (as provided; non-blank)
 * @param type the counterparty kind (party-type cadastro code)
 */
public record Party(String id, String type) {

  public Party {
    if (id == null || id.isBlank()) {
      throw new FinancePartyInvalidException();
    }
    if (type == null || type.isBlank()) {
      throw new FinancePartyInvalidException();
    }
    id = id.trim();
    type = type.trim();
  }
}
