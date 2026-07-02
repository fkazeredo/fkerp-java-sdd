package com.fksoft.domain.admin;

import com.fksoft.domain.finance.EntryTypeCodes;
import java.util.Map;

/**
 * The small set of {@code ADMIN_EXPENSE_KIND} code constants whose behavior the domain wires
 * (SPEC-0031 BR5; DL-0085/DL-0115). After {@code AdminExpenseKind} became an editable cadastro, the
 * kind→entry-type mapping (which drives the mandatory document via Compliance) is preserved here as
 * code constants — the cadastro owns the <em>extensible set + labels</em>, this class owns the
 * <em>wired behavior</em>. Since 18d the entry type is itself a cadastro code (String), so this
 * maps one code to another (SPEC-0031/DL-0118).
 *
 * <p>A brand-new code the operator adds with no wired mapping falls back to {@link
 * EntryTypeCodes#OTHER_EXPENSE} (no mandatory document at registration) — it works as pure
 * reference data until a later slice wires it. This is the documented seam of DL-0115.
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

  private static final Map<String, String> ENTRY_TYPE_BY_CODE =
      Map.of(
          UTILITY, EntryTypeCodes.UTILITY_EXPENSE,
          AUTONOMOUS_SERVICE, EntryTypeCodes.AUTONOMOUS_SERVICE,
          SERVICE, EntryTypeCodes.SERVICE,
          OTHER, EntryTypeCodes.OTHER_EXPENSE);

  private AdminExpenseCodes() {}

  /**
   * The Finance entry-type code an expense kind code posts as (DL-0085) — the key the Compliance
   * reads to decide which document is mandatory. An unknown code (a new cadastro item with no wired
   * mapping) falls back to {@link EntryTypeCodes#OTHER_EXPENSE} (DL-0115 seam).
   *
   * @param code the {@code ADMIN_EXPENSE_KIND} cadastro code
   * @return the mapped entry-type code (never {@code null})
   */
  public static String entryTypeFor(String code) {
    if (code == null) {
      return EntryTypeCodes.OTHER_EXPENSE;
    }
    return ENTRY_TYPE_BY_CODE.getOrDefault(code, EntryTypeCodes.OTHER_EXPENSE);
  }
}
