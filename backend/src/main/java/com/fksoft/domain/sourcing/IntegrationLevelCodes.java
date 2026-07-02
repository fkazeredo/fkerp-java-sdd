package com.fksoft.domain.sourcing;

/**
 * The {@code INTEGRATION_LEVEL} code constants whose behavior the domain wires (SPEC-0031 BR5;
 * DL-0117). After {@code IntegrationLevel} became an editable cadastro, the wired rule is
 * preserved: the inbound ACL registers a sourced offer with {@link #INBOUND} as part of the
 * INTEGRATED quoting branch (SPEC-0009 BR2/BR4; DL-0018) — that path must keep minting exactly
 * {@code INBOUND}, so a later relabel of the cadastro item never changes the integration flow. The
 * cadastro owns the extensible set + labels; this class owns the wired behavior.
 */
public final class IntegrationLevelCodes {

  /** No integration — the offer is handled manually. */
  public static final String NONE = "NONE";

  /**
   * Inbound only — the external system feeds the ERP (the quotation-site webhook). The INTEGRATED
   * quoting branch (DL-0018) mints an offer at this level.
   */
  public static final String INBOUND = "INBOUND";

  /** Two-way integration (the ERP both reads from and writes to the external system). */
  public static final String BIDIRECTIONAL = "BIDIRECTIONAL";

  private IntegrationLevelCodes() {}
}
