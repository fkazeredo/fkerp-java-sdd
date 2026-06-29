package com.fksoft.domain.finance;

/**
 * The counterparty of a ledger entry (SPEC-0015 BR1): an opaque id plus its {@link PartyType}. It
 * is a value — a reference to another context (Accounts/Suppliers) by id, never a cross-module FK.
 *
 * @param id the counterparty id (as provided; non-blank)
 * @param type the counterparty kind
 */
public record Party(String id, PartyType type) {

  public Party {
    if (id == null || id.isBlank()) {
      throw new FinancePartyInvalidException();
    }
    if (type == null) {
      throw new FinancePartyInvalidException();
    }
    id = id.trim();
  }
}
