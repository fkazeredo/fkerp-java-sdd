package com.fksoft.domain.admin;

import com.fksoft.domain.finance.EntryType;
import java.util.Map;

/**
 * The small set of {@code ADMIN_EXPENSE_KIND} code constants whose behavior the domain wires
 * (SPEC-0031 BR5; DL-0085/DL-0115). After {@code AdminExpenseKind} became an editable cadastro, the
 * kind→{@link EntryType} mapping (which drives the mandatory document via Compliance) is preserved
 * here as code constants — the cadastro owns the <em>extensible set + labels</em>, this class owns
 * the <em>wired behavior</em>.
 *
 * <p>A brand-new code the operator adds with no wired mapping falls back to {@link
 * EntryType#OTHER_EXPENSE} (no mandatory document at registration) — it works as pure reference data
 * until a later slice wires it. This is the documented seam of DL-0115.
 */
public final class AdminExpenseCodes {

  /** The cadastro code for a utility (water/power/telephone) → {@code UTILITY_EXPENSE}. */
  public static final String UTILITY = "UTILITY";

  /** The cadastro code for a self-employed (PF) service → {@code AUTONOMOUS_SERVICE} (RPA). */
  public static final String AUTONOMOUS_SERVICE = "AUTONOMOUS_SERVICE";

  /** The cadastro code for a PJ software/service → {@code SERVICE} (NFS-e). */
  public static final String SERVICE = "SERVICE";

  /** The cadastro code for a generic expense → {@code OTHER_EXPENSE} (no mandatory document). */
  public static final String OTHER = "OTHER";

  private static final Map<String, EntryType> ENTRY_TYPE_BY_CODE =
      Map.of(
          UTILITY, EntryType.UTILITY_EXPENSE,
          AUTONOMOUS_SERVICE, EntryType.AUTONOMOUS_SERVICE,
          SERVICE, EntryType.SERVICE,
          OTHER, EntryType.OTHER_EXPENSE);

  private AdminExpenseCodes() {}

  /**
   * The Finance {@link EntryType} an expense kind code posts as (DL-0085) — the key the Compliance
   * reads to decide which document is mandatory. An unknown code (a new cadastro item with no wired
   * mapping) falls back to {@link EntryType#OTHER_EXPENSE} (DL-0115 seam).
   *
   * @param code the {@code ADMIN_EXPENSE_KIND} cadastro code
   * @return the mapped entry type (never {@code null})
   */
  public static EntryType entryTypeFor(String code) {
    return ENTRY_TYPE_BY_CODE.getOrDefault(code, EntryType.OTHER_EXPENSE);
  }
}
