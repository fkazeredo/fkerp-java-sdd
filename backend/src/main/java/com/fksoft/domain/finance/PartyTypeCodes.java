package com.fksoft.domain.finance;

/**
 * The {@code PARTY_TYPE} code constants the domain wires (SPEC-0031 BR5; DL-0118). After {@code
 * PartyType} became an editable cadastro, the party type still identifies the kind of counterparty
 * of a ledger entry (SPEC-0015 BR1): an {@code AGENCY}/{@code AGENT} (Accounts), a {@code
 * SUPPLIER}, or {@code OTHER} (e.g. a utility or the tax authority). The internal producers emit
 * these constants; the cadastro owns the extensible set + labels. The type is stored as a value
 * alongside the party id — no cross-module FK.
 */
public final class PartyTypeCodes {

  /** An agency counterparty (Accounts). */
  public static final String AGENCY = "AGENCY";

  /** An agent counterparty (Accounts). */
  public static final String AGENT = "AGENT";

  /** A supplier counterparty. */
  public static final String SUPPLIER = "SUPPLIER";

  /** A generic counterparty (e.g. a utility or the municipal tax authority). */
  public static final String OTHER = "OTHER";

  private PartyTypeCodes() {}
}
