package com.fksoft.domain.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.finance.internal.AccountingPeriod;
import com.fksoft.domain.finance.internal.LedgerEntry;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Finance state machines (SPEC-0015 BR2/BR3/BR4): the ledger entry lifecycle
 * (PROVISIONAL→CONFIRMED→SETTLED, forward-only) and the period machine (OPEN→CLOSING→CLOSED).
 */
class PeriodAndEntryStateMachineTest {

  private static final Instant NOW = Instant.parse("2026-06-26T12:00:00Z");

  @Test
  void entryIsBornProvisional() {
    LedgerEntry entry = sampleEntry();

    assertThat(entry.status()).isEqualTo(EntryStatus.PROVISIONAL);
  }

  @Test
  void entryMovesProvisionalToConfirmedToSettled() {
    LedgerEntry entry = sampleEntry();

    entry.transitionTo(EntryStatus.CONFIRMED, NOW, "tester");
    assertThat(entry.status()).isEqualTo(EntryStatus.CONFIRMED);

    entry.transitionTo(EntryStatus.SETTLED, NOW, "tester");
    assertThat(entry.status()).isEqualTo(EntryStatus.SETTLED);
  }

  @Test
  void entryRejectsSkippingConfirmation() {
    LedgerEntry entry = sampleEntry();

    assertThatThrownBy(() -> entry.transitionTo(EntryStatus.SETTLED, NOW, "tester"))
        .isInstanceOf(FinanceEntryTransitionInvalidException.class);
  }

  @Test
  void settledEntryIsTerminal() {
    LedgerEntry entry = sampleEntry();
    entry.transitionTo(EntryStatus.CONFIRMED, NOW, "tester");
    entry.transitionTo(EntryStatus.SETTLED, NOW, "tester");

    assertThatThrownBy(() -> entry.transitionTo(EntryStatus.CONFIRMED, NOW, "tester"))
        .isInstanceOf(FinanceEntryTransitionInvalidException.class);
  }

  @Test
  void periodIsBornOpenAndSeals() {
    AccountingPeriod period = AccountingPeriod.open("2026-06");
    assertThat(period.status()).isEqualTo(PeriodStatus.OPEN);
    assertThat(period.isClosed()).isFalse();

    period.beginClosing();
    assertThat(period.status()).isEqualTo(PeriodStatus.CLOSING);

    period.close(NOW, "tester");
    assertThat(period.status()).isEqualTo(PeriodStatus.CLOSED);
    assertThat(period.isClosed()).isTrue();
  }

  @Test
  void abortedClosingReturnsToOpen() {
    AccountingPeriod period = AccountingPeriod.open("2026-06");
    period.beginClosing();

    period.abortClosing();

    assertThat(period.status()).isEqualTo(PeriodStatus.OPEN);
  }

  @Test
  void periodIdRejectsMalformedValue() {
    assertThatThrownBy(() -> AccountingPeriodId.of("2026/06"))
        .isInstanceOf(FinancePeriodInvalidException.class);
    assertThatThrownBy(() -> AccountingPeriodId.of("not-a-month"))
        .isInstanceOf(FinancePeriodInvalidException.class);
  }

  private static LedgerEntry sampleEntry() {
    return LedgerEntry.register(
        LedgerDirection.PAYABLE,
        new Party("sup-1", PartyType.SUPPLIER),
        Money.of(new BigDecimal("2850.00"), "BRL"),
        EntryType.SUPPLIER_SETTLEMENT,
        "2026-06",
        NOW,
        "tester");
  }
}
