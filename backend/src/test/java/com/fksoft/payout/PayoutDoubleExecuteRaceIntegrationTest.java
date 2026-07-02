package com.fksoft.payout;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.CreatePayoutCommand;
import com.fksoft.domain.payout.Payee;
import com.fksoft.domain.payout.PayeeTypeCodes;
import com.fksoft.domain.payout.PayoutAlreadyExecutedException;
import com.fksoft.domain.payout.PayoutKindCodes;
import com.fksoft.domain.payout.PayoutService;
import com.fksoft.domain.payout.PayoutView;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Concurrency proof for the payout execution lifecycle (SPEC-0017 BR2/BR3, Fase 19i/DL-0131): N
 * threads racing {@code beginInstallmentExecution} on a single-installment payout must produce
 * exactly ONE execution begin — the pessimistic lock serializes the writers and every loser gets
 * the domain "already executed" rejection, never a second gateway request.
 */
class PayoutDoubleExecuteRaceIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final int RACERS = 4;

  @Autowired private PayoutService payoutService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM payout_installments");
    jdbcTemplate.execute("DELETE FROM payouts");
  }

  @Test
  void concurrentExecutesBeginExactlyOneInstallment() throws Exception {
    PayoutView payout =
        payoutService.create(
            new CreatePayoutCommand(
                PayoutKindCodes.AGENT_COMMISSION,
                new Payee("ag-race", PayeeTypeCodes.AGENT),
                null,
                null,
                Money.of(new BigDecimal("500.00"), "BRL"),
                null,
                null,
                null,
                null),
            "tester");
    UUID payoutId = payout.id();

    AtomicInteger began = new AtomicInteger();
    AtomicInteger rejected = new AtomicInteger();
    CyclicBarrier start = new CyclicBarrier(RACERS);
    ExecutorService executor = Executors.newFixedThreadPool(RACERS);
    try {
      List<Future<?>> racers = new ArrayList<>();
      for (int i = 0; i < RACERS; i++) {
        racers.add(
            executor.submit(
                () -> {
                  start.await();
                  try {
                    payoutService.beginInstallmentExecution(payoutId);
                    began.incrementAndGet();
                  } catch (PayoutAlreadyExecutedException alreadyRunning) {
                    rejected.incrementAndGet();
                  }
                  return null;
                }));
      }
      for (Future<?> racer : racers) {
        racer.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(began).hasValue(1);
    assertThat(rejected).hasValue(RACERS - 1);
    // The database agrees: the single installment is EXECUTING exactly once.
    Integer executing =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM payout_installments WHERE payout_id = ? AND status = 'EXECUTING'",
            Integer.class,
            payoutId);
    assertThat(executing).isEqualTo(1);
  }
}
