package com.fksoft.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.booking.CancellationCharged;
import com.fksoft.domain.booking.CancellationTypeCodes;
import com.fksoft.domain.booking.Charge;
import com.fksoft.domain.booking.ChargeKindCodes;
import com.fksoft.domain.booking.CostBearer;
import com.fksoft.domain.booking.MerchantObligationIncurred;
import com.fksoft.domain.booking.NoShowCharged;
import com.fksoft.domain.finance.EntryTypeCodes;
import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.money.Money;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration tests for the event-driven AP/AR posting (SPEC-0015 BR5, DL-0041): the Finance module
 * consumes the Booking charge events and posts the corresponding ledger entries idempotently. These
 * publish the events directly (as the Booking module does in-process) so the mapping and the
 * idempotency can be asserted without driving the whole booking lifecycle; the end-to-end wiring
 * through {@code POST /api/bookings/{id}/cancel} is covered by the booking tests.
 */
class FinanceEventPostingIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ApplicationEventPublisher events;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM posted_event_entries");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM accounting_periods");
  }

  @Test
  void cancellationChargesBecomeApArEntriesInThePeriodOfTheFact() {
    UUID bookingId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-06-15T12:00:00Z");

    events.publishEvent(
        new CancellationCharged(
            bookingId,
            List.of(
                new Charge(
                    ChargeKindCodes.PENALTY,
                    Money.of(new BigDecimal("1350.00"), "BRL"),
                    CostBearer.AGENCY),
                new Charge(
                    ChargeKindCodes.CUSTOMER_REFUND,
                    Money.of(new BigDecimal("2700.00"), "BRL"),
                    CostBearer.ACME)),
            CancellationTypeCodes.STANDARD,
            occurredAt));

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT direction, entry_type, amount, currency, period, status "
                + "FROM ledger_entries WHERE party_id = ? ORDER BY entry_type",
            bookingId.toString());

    assertThat(rows).hasSize(2);
    // PENALTY -> RECEIVABLE, period from occurredAt (2026-06), PROVISIONAL.
    assertThat(rows)
        .anySatisfy(
            r -> {
              assertThat(r.get("direction")).isEqualTo(LedgerDirection.RECEIVABLE.name());
              assertThat(r.get("entry_type")).isEqualTo(EntryTypeCodes.PENALTY);
              assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("1350.00");
              assertThat(r.get("currency")).isEqualTo("BRL");
              assertThat(r.get("period")).isEqualTo("2026-06");
              assertThat(r.get("status")).isEqualTo("PROVISIONAL");
            })
        // CUSTOMER_REFUND -> PAYABLE / REFUND.
        .anySatisfy(
            r -> {
              assertThat(r.get("direction")).isEqualTo(LedgerDirection.PAYABLE.name());
              assertThat(r.get("entry_type")).isEqualTo(EntryTypeCodes.REFUND);
              assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("2700.00");
            });
  }

  @Test
  void reDeliveringTheSameCancellationEventDoesNotDoublePost() {
    UUID bookingId = UUID.randomUUID();
    CancellationCharged event =
        new CancellationCharged(
            bookingId,
            List.of(
                new Charge(
                    ChargeKindCodes.PENALTY,
                    Money.of(new BigDecimal("1350.00"), "BRL"),
                    CostBearer.AGENCY)),
            CancellationTypeCodes.STANDARD,
            Instant.parse("2026-06-15T12:00:00Z"));

    events.publishEvent(event);
    events.publishEvent(event); // same fact re-delivered

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE party_id = ?",
            Integer.class,
            bookingId.toString());
    assertThat(count).isEqualTo(1);
  }

  @Test
  void merchantObligationPostsTheSupplierPayableOnceAndCancellationSupplierIsNotDuplicated() {
    UUID bookingId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-06-15T12:00:00Z");
    Charge supplier =
        new Charge(
            ChargeKindCodes.SUPPLIER, Money.of(new BigDecimal("500.00"), "USD"), CostBearer.ACME);

    // ALL_SALES_FINAL publishes the supplier obligation in BOTH forms; it must post only ONCE.
    events.publishEvent(
        new CancellationCharged(
            bookingId,
            List.of(
                supplier,
                new Charge(
                    ChargeKindCodes.CUSTOMER_REFUND,
                    Money.of(new BigDecimal("2700.00"), "BRL"),
                    CostBearer.ACME)),
            CancellationTypeCodes.ALL_SALES_FINAL,
            occurredAt));
    events.publishEvent(new MerchantObligationIncurred(bookingId, supplier, occurredAt));

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT direction, entry_type, amount, currency FROM ledger_entries "
                + "WHERE party_id = ? ORDER BY entry_type",
            bookingId.toString());

    // Exactly two entries: ONE supplier PAYABLE (not duplicated) AND the customer REFUND — the
    // merchant trap, preserved (DL-0024): they coexist, never netted.
    assertThat(rows).hasSize(2);
    long supplierEntries =
        rows.stream()
            .filter(r -> EntryTypeCodes.SUPPLIER_SETTLEMENT.equals(r.get("entry_type")))
            .count();
    assertThat(supplierEntries).isEqualTo(1);
    assertThat(rows)
        .anySatisfy(
            r -> {
              assertThat(r.get("entry_type")).isEqualTo(EntryTypeCodes.SUPPLIER_SETTLEMENT);
              assertThat(r.get("direction")).isEqualTo(LedgerDirection.PAYABLE.name());
              assertThat((BigDecimal) r.get("amount")).isEqualByComparingTo("500.00");
              assertThat(r.get("currency")).isEqualTo("USD");
            })
        .anySatisfy(r -> assertThat(r.get("entry_type")).isEqualTo(EntryTypeCodes.REFUND));
  }

  @Test
  void noShowWithFeePostsAReceivablePenaltyAndWaivedPostsNothing() {
    UUID charged = UUID.randomUUID();
    UUID waived = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-06-15T12:00:00Z");

    events.publishEvent(
        new NoShowCharged(charged, Money.of(new BigDecimal("80.00"), "BRL"), false, occurredAt));
    events.publishEvent(new NoShowCharged(waived, null, true, occurredAt));

    Integer chargedCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE party_id = ?",
            Integer.class,
            charged.toString());
    Integer waivedCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM ledger_entries WHERE party_id = ?",
            Integer.class,
            waived.toString());

    assertThat(chargedCount).isEqualTo(1);
    assertThat(waivedCount).isEqualTo(0);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT direction, entry_type FROM ledger_entries WHERE party_id = ?",
            charged.toString());
    assertThat(row.get("direction")).isEqualTo(LedgerDirection.RECEIVABLE.name());
    assertThat(row.get("entry_type")).isEqualTo(EntryTypeCodes.PENALTY);
  }
}
