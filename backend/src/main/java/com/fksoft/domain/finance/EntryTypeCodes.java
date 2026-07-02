package com.fksoft.domain.finance;

/**
 * The {@code ENTRY_TYPE} code constants whose behavior the domain wires (SPEC-0031 BR5; DL-0118).
 * After {@code EntryType} became an editable cadastro, the entry type still identifies the business
 * nature of a ledger entry (SPEC-0015 BR1) and is the key the Compliance reads to decide which
 * document is mandatory at registration (SPEC-0008 BR4; DL-0012). It also drives the AP/AR posting
 * of the internal producers (the Booking/Payout/Billing listeners emit these constants).
 *
 * <p>The cadastro owns the extensible set + labels; this class owns the wired values. The entry
 * type crosses the module boundary as a value (its {@code code}), never as a shared enum reference,
 * so Compliance stays decoupled from Finance's type system. A brand-new code the operator adds with
 * no wired mapping flows as pure reference data (it simply has no mandatory document at
 * registration until a later slice wires it — the documented DL-0115 seam).
 */
public final class EntryTypeCodes {

  /** A commission the Acme is owed (RECEIVABLE) — the issued commission invoice. */
  public static final String COMMISSION_RECEIVABLE = "COMMISSION_RECEIVABLE";

  /** A commission the Acme owes the agent (PAYABLE) — the payout repass baixa. */
  public static final String COMMISSION_PAYABLE = "COMMISSION_PAYABLE";

  /** A cancellation/no-show penalty (RECEIVABLE). */
  public static final String PENALTY = "PENALTY";

  /** A utility (water/power/telephone) administrative expense (PAYABLE). */
  public static final String UTILITY_EXPENSE = "UTILITY_EXPENSE";

  /** A self-employed (PF) service expense — RPA (PAYABLE). */
  public static final String AUTONOMOUS_SERVICE = "AUTONOMOUS_SERVICE";

  /** A supplier settlement baixa (PAYABLE). */
  public static final String SUPPLIER_SETTLEMENT = "SUPPLIER_SETTLEMENT";

  /** A customer refund baixa (PAYABLE). */
  public static final String REFUND = "REFUND";

  /** A tax to remit (e.g. ISS/withholdings from an issued commission invoice — DL-0047). */
  public static final String TAX_PAYABLE = "TAX_PAYABLE";

  /** A recurring PJ software/service administrative expense — its NFS-e is required (DL-0085). */
  public static final String SERVICE = "SERVICE";

  /** A generic administrative expense with no standardized supporting document (DL-0085). */
  public static final String OTHER_EXPENSE = "OTHER_EXPENSE";

  private EntryTypeCodes() {}
}
