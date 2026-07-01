package com.fksoft.domain.billing;

/**
 * The set of {@code TAX_REGIME} code constants the Billing domain wires (SPEC-0031 BR5; DL-0044/
 * DL-0115). After {@code TaxRegime} became an editable cadastro, the regime still selects the {@link
 * TaxRegimeStrategy} (Simples/Presumido/Real compute ISS and withholdings differently) — that
 * branching is preserved here as code constants. The cadastro owns the extensible set + labels; this
 * class owns the wired behavior.
 *
 * <p>The v1 default is {@link #SIMPLES_NACIONAL} (DL-0044): the most common in PME and the most
 * defensible until the accountant confirms the real regime. A Presumido/Real strategy is plugged in
 * by wiring a matching {@link TaxRegimeStrategy} bean — no domain refactor.
 */
public final class TaxRegimeCodes {

  /** Simples Nacional — the v1 default; no federal withholdings on the commission (DL-0044). */
  public static final String SIMPLES_NACIONAL = "SIMPLES_NACIONAL";

  /** Lucro Presumido — pluggable strategy when the accountant confirms it. */
  public static final String LUCRO_PRESUMIDO = "LUCRO_PRESUMIDO";

  /** Lucro Real — pluggable strategy when the accountant confirms it. */
  public static final String LUCRO_REAL = "LUCRO_REAL";

  private TaxRegimeCodes() {}
}
