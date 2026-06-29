package com.fksoft.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.CreateLedgerEntryRequest;
import com.fksoft.application.api.dto.CreateLedgerEntryRequest.PartyRequest;
import com.fksoft.domain.finance.EntryStatus;
import com.fksoft.domain.finance.EntryType;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.finance.LedgerEntryView;
import com.fksoft.domain.finance.PartyType;
import com.fksoft.domain.finance.PeriodStatus;
import com.fksoft.domain.finance.PeriodView;
import com.fksoft.domain.money.Money;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the Finance module (SPEC-0015): create AP/AR entries (PROVISIONAL), confirm
 * them, close a period (with the default permissive guard in this slice — the real Compliance veto
 * arrives in slice 7c), reject a malformed period, and reject an entry against a CLOSED period.
 */
class FinanceIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
  }

  @Test
  void createsPayableEntryAsProvisional() {
    ResponseEntity<LedgerEntryView> response =
        restTemplate.postForEntity(
            "/api/finance/entries",
            payable("sup-12", "2850.00", EntryType.SUPPLIER_SETTLEMENT, "2026-06"),
            LedgerEntryView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    LedgerEntryView entry = response.getBody();
    assertThat(entry).isNotNull();
    assertThat(entry.status()).isEqualTo(EntryStatus.PROVISIONAL);
    assertThat(entry.direction()).isEqualTo(LedgerDirection.PAYABLE);
    assertThat(entry.amount()).isEqualTo(Money.of(new BigDecimal("2850.00"), "BRL"));
    assertThat(entry.period()).isEqualTo("2026-06");
  }

  @Test
  void confirmsAProvisionalEntry() {
    LedgerEntryView entry = createEntry("2026-06");

    ResponseEntity<LedgerEntryView> confirmed =
        restTemplate.postForEntity(
            "/api/finance/entries/" + entry.id() + "/confirm", null, LedgerEntryView.class);

    assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(confirmed.getBody()).isNotNull();
    assertThat(confirmed.getBody().status()).isEqualTo(EntryStatus.CONFIRMED);
  }

  @Test
  void closesAnEmptyPeriodWhenNothingVetoes() {
    // An empty period has no non-conformant entries, so the Compliance veto allows the close.
    ResponseEntity<PeriodView> response =
        restTemplate.postForEntity("/api/finance/periods/2026-07/close", null, PeriodView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(PeriodStatus.CLOSED);
    assertThat(response.getBody().closedAt()).isNotNull();
  }

  @Test
  void rejectsAnEntryAgainstAClosedPeriod() {
    // Close an empty period (no veto), then a new entry against it must be rejected (BR4).
    restTemplate.postForEntity("/api/finance/periods/2026-08/close", null, PeriodView.class);

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/finance/entries",
            payable("sup-9", "100.00", EntryType.SUPPLIER_SETTLEMENT, "2026-08"),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("finance.period.closed");
  }

  @Test
  void rejectsAMalformedPeriod() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/finance/entries",
            payable("sup-9", "100.00", EntryType.SUPPLIER_SETTLEMENT, "2026/06"),
            ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("finance.period.invalid");
  }

  @Test
  void totalsPeriodPerCurrency() {
    restTemplate.postForEntity(
        "/api/finance/entries",
        payable("sup-1", "1000.00", EntryType.SUPPLIER_SETTLEMENT, "2026-09"),
        LedgerEntryView.class);
    restTemplate.postForEntity(
        "/api/finance/entries",
        new CreateLedgerEntryRequest(
            LedgerDirection.RECEIVABLE,
            new PartyRequest("ag-1", PartyType.AGENCY),
            Money.of(new BigDecimal("2700.00"), "BRL"),
            EntryType.COMMISSION_RECEIVABLE,
            "2026-09"),
        LedgerEntryView.class);

    PeriodView period =
        restTemplate.getForEntity("/api/finance/periods/2026-09", PeriodView.class).getBody();

    assertThat(period).isNotNull();
    assertThat(period.status()).isEqualTo(PeriodStatus.OPEN);
    assertThat(period.payableTotals()).contains(Money.of(new BigDecimal("1000.00"), "BRL"));
    assertThat(period.receivableTotals()).contains(Money.of(new BigDecimal("2700.00"), "BRL"));
  }

  private LedgerEntryView createEntry(String period) {
    LedgerEntryView entry =
        restTemplate
            .postForEntity(
                "/api/finance/entries",
                payable("sup-12", "2850.00", EntryType.SUPPLIER_SETTLEMENT, period),
                LedgerEntryView.class)
            .getBody();
    assertThat(entry).isNotNull();
    return entry;
  }

  private static CreateLedgerEntryRequest payable(
      String partyId, String amount, EntryType type, String period) {
    return new CreateLedgerEntryRequest(
        LedgerDirection.PAYABLE,
        new PartyRequest(partyId, PartyType.SUPPLIER),
        Money.of(new BigDecimal(amount), "BRL"),
        type,
        period);
  }
}
