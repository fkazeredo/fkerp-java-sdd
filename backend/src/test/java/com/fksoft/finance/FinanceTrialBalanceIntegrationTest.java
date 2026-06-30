package com.fksoft.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.CreateLedgerEntryRequest;
import com.fksoft.application.api.dto.CreateLedgerEntryRequest.PartyRequest;
import com.fksoft.domain.finance.EntryStatus;
import com.fksoft.domain.finance.EntryType;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.LedgerEntryView;
import com.fksoft.domain.finance.PartyType;
import com.fksoft.domain.finance.TrialBalanceView;
import com.fksoft.domain.money.Money;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the period trial-balance (SPEC-0015 BR10, DL-0043): {@code GET
 * /api/finance/periods/{yyyymm}/trial-balance} returns the operational balance per currency
 * (payable, receivable, net = receivable − payable) and the counts per status, never summing
 * different currencies (DL-0013).
 */
class FinanceTrialBalanceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM posted_event_entries");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
  }

  @Test
  void trialBalanceAggregatesPerCurrencyWithNetAndStatusCounts() {
    // BRL: 2700 receivable, 1000 payable -> net 1700. USD: 500 payable -> net -500.
    create(LedgerDirection.RECEIVABLE, "ag-1", PartyType.AGENCY, "2700.00", "BRL");
    create(LedgerDirection.PAYABLE, "sup-1", PartyType.SUPPLIER, "1000.00", "BRL");
    create(LedgerDirection.PAYABLE, "sup-2", PartyType.SUPPLIER, "500.00", "USD");

    ResponseEntity<TrialBalanceView> response =
        restTemplate.getForEntity(
            "/api/finance/periods/2026-10/trial-balance", TrialBalanceView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TrialBalanceView body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.period()).isEqualTo("2026-10");

    assertThat(body.balances())
        .anySatisfy(
            b -> {
              assertThat(b.currency()).isEqualTo("BRL");
              assertThat(b.payable()).isEqualByComparingTo("1000.00");
              assertThat(b.receivable()).isEqualByComparingTo("2700.00");
              assertThat(b.net()).isEqualByComparingTo("1700.00");
            })
        .anySatisfy(
            b -> {
              assertThat(b.currency()).isEqualTo("USD");
              assertThat(b.payable()).isEqualByComparingTo("500.00");
              assertThat(b.receivable()).isEqualByComparingTo("0.00");
              assertThat(b.net()).isEqualByComparingTo("-500.00");
            });

    // Three PROVISIONAL entries, none confirmed/settled.
    assertThat(body.provisionalCount()).isEqualTo(3);
    assertThat(body.confirmedCount()).isEqualTo(0);
    assertThat(body.settledCount()).isEqualTo(0);
  }

  @Test
  void trialBalanceOfAnUntouchedPeriodIsEmpty() {
    TrialBalanceView body =
        restTemplate
            .getForEntity("/api/finance/periods/2030-01/trial-balance", TrialBalanceView.class)
            .getBody();

    assertThat(body).isNotNull();
    assertThat(body.balances()).isEmpty();
    assertThat(body.provisionalCount()).isEqualTo(0);
  }

  private void create(
      LedgerDirection direction, String partyId, PartyType type, String amount, String currency) {
    LedgerEntryView view =
        restTemplate
            .postForEntity(
                "/api/finance/entries",
                new CreateLedgerEntryRequest(
                    direction,
                    new PartyRequest(partyId, type),
                    Money.of(new BigDecimal(amount), currency),
                    direction == LedgerDirection.PAYABLE
                        ? EntryType.SUPPLIER_SETTLEMENT
                        : EntryType.COMMISSION_RECEIVABLE,
                    "2026-10"),
                LedgerEntryView.class)
            .getBody();
    assertThat(view).isNotNull();
    assertThat(view.status()).isEqualTo(EntryStatus.PROVISIONAL);
  }
}
