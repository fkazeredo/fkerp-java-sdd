package com.fksoft.domain.intelligence;

/**
 * The small set of Intelligence reference-data code constants whose behavior the domain wires
 * (SPEC-0031 BR5; DL-0116). After {@code SubjectKind}, {@code InsightType} and {@code Verdict}
 * became editable cadastros, the codes the deterministic advisor/narrator/validation branch on are
 * preserved here as code constants — the cadastro owns the extensible set + labels, this class owns
 * the wired behavior.
 *
 * <p>These three are <strong>system-produced</strong> values (the DSS mints them from consumed
 * events; they are never accepted as a create payload). They are still cadastros so their pt-BR
 * labels are editable and the screens render a label. The only wire input is the {@code
 * InsightType} list filter, which crosses the boundary as a plain string (no create validation
 * needed — an unknown filter simply matches nothing).
 */
public final class IntelligenceCodes {

  // --- SubjectKind (the axis an insight is about) ---

  /** The agency axis — the only axis derivable from the events the module consumes (DL-0034). */
  public static final String AGENCY = "AGENCY";

  /** The route axis (designed-in seam; not produced in v1). */
  public static final String ROUTE = "ROUTE";

  /** The product axis (designed-in seam; not produced in v1). */
  public static final String PRODUCT = "PRODUCT";

  /** The supplier axis (designed-in seam; not produced in v1). */
  public static final String SUPPLIER = "SUPPLIER";

  // --- InsightType (the kind of insight the DSS produces) ---

  /** The FX-freeze promo advisor insight (BR5, DL-0035). */
  public static final String PROMO_FX_ADVISOR = "PROMO_FX_ADVISOR";

  /** The commission-tier nudge insight (BR6, DL-0036; gated behind a feature flag). */
  public static final String OVERRIDE_NUDGE = "OVERRIDE_NUDGE";

  // --- Verdict (the PromoFxAdvisor verdict) ---

  /** The promo converted: keep it (the realized gap stayed non-negative with enough volume). */
  public static final String CONVERTE = "CONVERTE";

  /** The promo only burned margin beyond the tolerated threshold: tighten it. */
  public static final String QUEIMA_MARGEM = "QUEIMA_MARGEM";

  private IntelligenceCodes() {}
}
