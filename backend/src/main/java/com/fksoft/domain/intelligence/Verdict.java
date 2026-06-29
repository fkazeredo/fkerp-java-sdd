package com.fksoft.domain.intelligence;

/**
 * The {@code PromoFxAdvisor} verdict (SPEC-0013 BR5, DL-0035): whether the FX freeze promotion paid
 * for itself for the subject.
 *
 * <ul>
 *   <li>{@code CONVERTE} — the promo converted: keep it (the realized gap stayed non-negative with
 *       enough attracted volume).
 *   <li>{@code QUEIMA_MARGEM} — it only burned margin beyond the tolerated threshold: tighten it.
 * </ul>
 *
 * <p>When neither holds, the advisor returns no verdict (no insight) so it does not add noise.
 */
public enum Verdict {
  CONVERTE,
  QUEIMA_MARGEM
}
