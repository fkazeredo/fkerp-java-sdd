package com.fksoft.domain.billing;

/**
 * The set of {@code WITHHOLDING_KIND} code constants the Billing domain wires (SPEC-0031 BR5;
 * DL-0044/DL-0115). After {@code WithholdingKind} became an editable cadastro, the withholding
 * lines a Presumido/Real strategy may populate are still identified by these codes (the {@link
 * WithholdingsCodec} (de)serializes them). Under Simples Nacional the withholdings list is empty
 * (DL-0044). The cadastro owns the extensible set + labels; this class owns the wired behavior.
 */
public final class WithholdingKindCodes {

  /** Federal income tax withheld at source. */
  public static final String IRRF = "IRRF";

  /** PIS contribution withheld. */
  public static final String PIS = "PIS";

  /** COFINS contribution withheld. */
  public static final String COFINS = "COFINS";

  /** CSLL (social contribution on net profit) withheld. */
  public static final String CSLL = "CSLL";

  /** Municipal ISS withheld by the taker. */
  public static final String ISS_RETIDO = "ISS_RETIDO";

  private WithholdingKindCodes() {}
}
