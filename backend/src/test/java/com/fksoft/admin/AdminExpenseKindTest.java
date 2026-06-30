package com.fksoft.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.admin.AdminExpenseKind;
import com.fksoft.domain.finance.EntryType;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the expense-kind → Finance entry-type mapping (SPEC-0025 BR3; DL-0085). This is the
 * load-bearing translation that makes a recurring expense fall into the right ledger type (and thus
 * require the right document); it must stay exactly as the decision log fixed it.
 */
class AdminExpenseKindTest {

  @Test
  void eachKindMapsToTheExpectedEntryType() {
    assertThat(AdminExpenseKind.UTILITY.entryType()).isEqualTo(EntryType.UTILITY_EXPENSE);
    assertThat(AdminExpenseKind.AUTONOMOUS_SERVICE.entryType())
        .isEqualTo(EntryType.AUTONOMOUS_SERVICE);
    assertThat(AdminExpenseKind.SERVICE.entryType()).isEqualTo(EntryType.SERVICE);
    assertThat(AdminExpenseKind.OTHER.entryType()).isEqualTo(EntryType.OTHER_EXPENSE);
  }
}
