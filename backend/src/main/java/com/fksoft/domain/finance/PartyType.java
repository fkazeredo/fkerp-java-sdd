package com.fksoft.domain.finance;

/**
 * The kind of counterparty of a ledger entry (SPEC-0015 BR1): an {@code AGENCY}/{@code AGENT}
 * (Accounts), a {@code SUPPLIER}, or {@code OTHER} (e.g. a utility). Stored as a value alongside
 * the party id — no cross-module FK.
 */
public enum PartyType {
  AGENCY,
  AGENT,
  SUPPLIER,
  OTHER
}
