package com.fksoft.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.domain.finance.AccountingPeriodId;
import com.fksoft.domain.finance.AccountingPeriodRepository;
import com.fksoft.domain.finance.EntryTypeCodes;
import com.fksoft.domain.finance.FinancePeriodClosedException;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.Party;
import com.fksoft.domain.finance.PartyTypeCodes;
import com.fksoft.domain.money.Money;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrency regression for the monthly close (SPEC-0015 BR4/BR4-bis, Fase 19i/DL-0131): an entry
 * registration racing {@code closePeriod} must serialize on the period row lock — either the entry
 * lands before the seal, or it re-reads CLOSED and is rejected. Before the 19i fix {@code register}
 * read the period <em>without</em> the lock, so an entry could slip into a period sealed between
 * the read and the insert; this test failed with an entry inside a CLOSED period.
 */
class FinanceClosePostRaceIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final String PERIOD = "2031-01";

  @Autowired private FinanceService financeService;
  @Autowired private AccountingPeriodRepository periods;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
  }

  @Test
  void registerRacingACloseNeverSlipsAnEntryIntoTheSealedPeriod() throws Exception {
    // An empty OPEN period pre-exists so both sides contend on the same row lock.
    jdbcTemplate.update(
        "INSERT INTO accounting_periods (period, status, version) VALUES (?, 'OPEN', 0)", PERIOD);

    CountDownLatch lockHeld = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      // The closing side: grabs the period row lock, signals, keeps the lock long enough for the
      // register to arrive mid-close, then seals CLOSED and commits.
      Future<?> close =
          executor.submit(
              () ->
                  new TransactionTemplate(transactionManager)
                      .executeWithoutResult(
                          tx -> {
                            periods.findByIdForUpdate(PERIOD).orElseThrow();
                            lockHeld.countDown();
                            sleep(700);
                            financeService.closePeriod(new AccountingPeriodId(PERIOD), "tester");
                          }));

      // The registering side: arrives while the close holds the lock. With the 19i fix it blocks
      // on findByIdForUpdate, re-reads the sealed period after the commit and is rejected (BR4).
      assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();
      assertThatThrownBy(
              () ->
                  financeService.register(
                      LedgerDirection.PAYABLE,
                      new Party("sup-race", PartyTypeCodes.SUPPLIER),
                      Money.of(new BigDecimal("100.00"), "BRL"),
                      EntryTypeCodes.SUPPLIER_SETTLEMENT,
                      new AccountingPeriodId(PERIOD),
                      "tester"))
          .isInstanceOf(FinancePeriodClosedException.class);
      close.get(10, TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    // The invariant that matters: the sealed period holds no raced entry.
    Integer entriesInPeriod =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE period = ?", Integer.class, PERIOD);
    String status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM accounting_periods WHERE period = ?", String.class, PERIOD);
    assertThat(entriesInPeriod).isZero();
    assertThat(status).isEqualTo("CLOSED");
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
