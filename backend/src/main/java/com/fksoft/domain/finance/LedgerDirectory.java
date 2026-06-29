package com.fksoft.domain.finance;

import java.util.List;

/**
 * Public cross-module port of the Finance module: lets the Compliance (SPEC-0008) read the ledger
 * entries of a period — by value, through snapshots — to run the close-check without touching
 * Finance's repository or entities (Spring Modulith). This is the only sanctioned way for another
 * module to read the ledger.
 */
public interface LedgerDirectory {

  /**
   * The entries registered in the given period (any status), as snapshots.
   *
   * @param period the period ({@code YYYY-MM})
   * @return the entry snapshots (empty when none)
   */
  List<LedgerEntrySnapshot> entriesOfPeriod(String period);
}
