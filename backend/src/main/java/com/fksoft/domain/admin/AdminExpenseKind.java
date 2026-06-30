package com.fksoft.domain.admin;

import com.fksoft.domain.finance.EntryType;

/**
 * The business kind of a recurring administrative expense (SPEC-0025 BR3; DL-0085) — the language
 * of the administrative desk. It deterministically maps to the Finance {@link EntryType} (the key
 * the Compliance uses to require the right document), keeping the kind→type translation inside
 * Admin (the modules stay decoupled; the value that crosses the boundary is the {@code EntryType}).
 *
 * <ul>
 *   <li>{@code UTILITY} (water/power/telephone) → {@link EntryType#UTILITY_EXPENSE} (UTILITY_BILL)
 *   <li>{@code AUTONOMOUS_SERVICE} (self-employed PF) → {@link EntryType#AUTONOMOUS_SERVICE} (RPA)
 *   <li>{@code SERVICE} (software/service PJ) → {@link EntryType#SERVICE} (NFSE)
 *   <li>{@code OTHER} → {@link EntryType#OTHER_EXPENSE} (no mandatory document at registration)
 * </ul>
 */
public enum AdminExpenseKind {
  UTILITY(EntryType.UTILITY_EXPENSE),
  AUTONOMOUS_SERVICE(EntryType.AUTONOMOUS_SERVICE),
  SERVICE(EntryType.SERVICE),
  OTHER(EntryType.OTHER_EXPENSE);

  private final EntryType entryType;

  AdminExpenseKind(EntryType entryType) {
    this.entryType = entryType;
  }

  /**
   * The Finance entry type this expense kind posts as (DL-0085) — the value the Compliance reads to
   * decide which document is mandatory.
   *
   * @return the mapped {@link EntryType}
   */
  public EntryType entryType() {
    return entryType;
  }
}
