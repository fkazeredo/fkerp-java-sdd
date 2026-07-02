package com.fksoft.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.finance.EntryTypeCodes;
import com.fksoft.domain.finance.FinanceService;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.Party;
import com.fksoft.domain.finance.PartyTypeCodes;
import com.fksoft.domain.money.Money;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Timezone hardening for the accounting period derivation (SPEC-0015 BR5, Fase 19i/DL-0131): the
 * period of an event-posted entry comes from {@code occurredAt} <strong>at UTC</strong> — never
 * from the JVM default timezone. The regression scenario: a fact at {@code 2031-02-01T01:30Z} is
 * still {@code 2031-01-31 22:30} in São Paulo (UTC-3); a naive implementation using the default
 * zone would book it into January. Both tests pin the default TZ to São Paulo to prove immunity.
 */
class FinancePeriodTimezoneIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private FinanceService financeService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM posted_event_entries");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
  }

  @Test
  void postsIntoTheUtcPeriodEvenWhenTheDefaultZoneIsStillInThePreviousMonth() {
    TimeZone original = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    try {
      // 01:30Z on Feb 1st = Jan 31st 22:30 in São Paulo. UTC convention → period 2031-02.
      financeService.postFromCharge(
          "tz-fact-1",
          "TZ_TEST",
          LedgerDirection.PAYABLE,
          new Party("sup-tz", PartyTypeCodes.SUPPLIER),
          Money.of(new BigDecimal("10.00"), "BRL"),
          EntryTypeCodes.SUPPLIER_SETTLEMENT,
          Instant.parse("2031-02-01T01:30:00Z"));
    } finally {
      TimeZone.setDefault(original);
    }

    String period =
        jdbcTemplate.queryForObject(
            "SELECT period FROM ledger_entries WHERE created_by = 'system'", String.class);
    assertThat(period).isEqualTo("2031-02");
  }

  @Test
  void aFactLateInTheUtcMonthStaysInThatMonthRegardlessOfTheDefaultZone() {
    TimeZone original = TimeZone.getDefault();
    // An east-of-UTC zone where 23:30Z of Jan 31st is already Feb 1st (02:30+03:00).
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"));
    try {
      financeService.postFromCharge(
          "tz-fact-2",
          "TZ_TEST",
          LedgerDirection.PAYABLE,
          new Party("sup-tz", PartyTypeCodes.SUPPLIER),
          Money.of(new BigDecimal("10.00"), "BRL"),
          EntryTypeCodes.SUPPLIER_SETTLEMENT,
          Instant.parse("2031-01-31T23:30:00Z"));
    } finally {
      TimeZone.setDefault(original);
    }

    String period =
        jdbcTemplate.queryForObject(
            "SELECT period FROM ledger_entries WHERE created_by = 'system'", String.class);
    assertThat(period).isEqualTo("2031-01");
  }
}
