package com.fksoft.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.admin.AdminExpenseCodes;
import com.fksoft.domain.finance.EntryType;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the expense-kind code → Finance entry-type mapping (SPEC-0025 BR3; DL-0085; after
 * the enum→cadastro conversion, SPEC-0031/DL-0115). This is the load-bearing translation that makes
 * a recurring expense fall into the right ledger type (and thus require the right document); it must
 * stay exactly as the decision log fixed it. A brand-new code with no wired mapping falls back to
 * {@code OTHER_EXPENSE} (the DL-0115 seam).
 */
class AdminExpenseCodesTest {

  @Test
  void eachKnownCodeMapsToTheExpectedEntryType() {
    assertThat(AdminExpenseCodes.entryTypeFor(AdminExpenseCodes.UTILITY))
        .isEqualTo(EntryType.UTILITY_EXPENSE);
    assertThat(AdminExpenseCodes.entryTypeFor(AdminExpenseCodes.AUTONOMOUS_SERVICE))
        .isEqualTo(EntryType.AUTONOMOUS_SERVICE);
    assertThat(AdminExpenseCodes.entryTypeFor(AdminExpenseCodes.SERVICE))
        .isEqualTo(EntryType.SERVICE);
    assertThat(AdminExpenseCodes.entryTypeFor(AdminExpenseCodes.OTHER))
        .isEqualTo(EntryType.OTHER_EXPENSE);
  }

  @Test
  void anUnknownCodeFallsBackToOtherExpense() {
    // A new cadastro item the operator adds with no wired mapping works as data (DL-0115 seam).
    assertThat(AdminExpenseCodes.entryTypeFor("BRAND_NEW_CODE"))
        .isEqualTo(EntryType.OTHER_EXPENSE);
    assertThat(AdminExpenseCodes.entryTypeFor(null)).isEqualTo(EntryType.OTHER_EXPENSE);
  }
}
