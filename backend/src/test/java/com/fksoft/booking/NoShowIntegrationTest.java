package com.fksoft.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.CancellationPolicyRequest;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.CreateBookingRequest;
import com.fksoft.application.api.dto.CreateBookingRequest.LocatorRequest;
import com.fksoft.application.api.dto.NoShowRequest;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.booking.BookingStatus;
import com.fksoft.domain.booking.BookingView;
import com.fksoft.domain.booking.CancellationTypeCodes;
import com.fksoft.domain.booking.ChargeKindCodes;
import com.fksoft.domain.booking.CostBearer;
import com.fksoft.domain.booking.LocatorOrigin;
import com.fksoft.domain.booking.NoShowResult;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the no-show policy (SPEC-0010 BR6): a no-show charges the configured fee
 * (recording a NO_SHOW charge); a no-show with proof of a cancelled flight on a {@code
 * waivedIfFlightCancelled} policy waives the fee (no charge).
 */
class NoShowIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    // Finance now posts AP/AR ledger entries from the no-show events (SPEC-0015 BR5, DL-0041);
    // clear them so a no-show test does not leak entries into period-keyed Finance tests.
    jdbcTemplate.execute("DELETE FROM posted_event_entries");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM cancellation_charges");
    jdbcTemplate.execute("DELETE FROM booking_cancellation_snapshots");
    jdbcTemplate.execute("DELETE FROM cancellation_policies");
    jdbcTemplate.execute("DELETE FROM reconciliation_cases");
    jdbcTemplate.execute("DELETE FROM bookings");
    jdbcTemplate.execute("DELETE FROM override_records");
    jdbcTemplate.execute("DELETE FROM quotes");
    jdbcTemplate.execute("DELETE FROM accounts");
    jdbcTemplate.execute("DELETE FROM pinned_sell_rates");
  }

  @Test
  void noShowChargesTheConfiguredFee() {
    String scope = "CAR-NOSHOW-FEE";
    putNoShowPolicy(scope, new BigDecimal("90.00"), false);
    String bookingId = confirmedBooking(scope);

    NoShowResult result =
        restTemplate
            .postForEntity(
                "/api/bookings/" + bookingId + "/no-show",
                new NoShowRequest(false),
                NoShowResult.class)
            .getBody();

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(BookingStatus.NO_SHOW);
    assertThat(result.waived()).isFalse();
    assertThat(result.charge()).isNotNull();
    assertThat(result.charge().kind()).isEqualTo(ChargeKindCodes.NO_SHOW);
    assertThat(result.charge().amount()).isEqualTo(Money.of(new BigDecimal("90.00"), "BRL"));

    Integer rows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM cancellation_charges WHERE booking_id = ? AND kind = 'NO_SHOW'",
            Integer.class,
            java.util.UUID.fromString(bookingId));
    assertThat(rows).isEqualTo(1);
  }

  @Test
  void noShowWaivesTheFeeWithProofOfACancelledFlight() {
    String scope = "CAR-NOSHOW-WAIVE";
    putNoShowPolicy(scope, new BigDecimal("90.00"), true);
    String bookingId = confirmedBooking(scope);

    NoShowResult result =
        restTemplate
            .postForEntity(
                "/api/bookings/" + bookingId + "/no-show",
                new NoShowRequest(true),
                NoShowResult.class)
            .getBody();

    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(BookingStatus.NO_SHOW);
    assertThat(result.waived()).isTrue();
    assertThat(result.charge()).isNull();

    Integer rows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM cancellation_charges WHERE booking_id = ? AND kind = 'NO_SHOW'",
            Integer.class,
            java.util.UUID.fromString(bookingId));
    assertThat(rows).isZero();
  }

  private void putNoShowPolicy(String scope, BigDecimal fee, boolean waived) {
    restTemplate.put(
        "/api/products/" + scope + "/cancellation-policy",
        new CancellationPolicyRequest(
            CancellationTypeCodes.STANDARD,
            List.of(),
            true,
            CostBearer.AGENCY,
            false,
            Money.of(fee, "BRL"),
            waived));
  }

  private String confirmedBooking(String scope) {
    String quoteId = createQuote();
    BookingView booking =
        restTemplate
            .postForEntity(
                "/api/bookings",
                new CreateBookingRequest(
                    java.util.UUID.fromString(quoteId),
                    new LocatorRequest(LocatorOrigin.EXTERNAL, "LOC-" + scope),
                    scope),
                BookingView.class)
            .getBody();
    assertThat(booking).isNotNull();
    String id = booking.id().toString();
    restTemplate.postForEntity("/api/bookings/" + id + "/pending", null, BookingView.class);
    restTemplate.postForEntity("/api/bookings/" + id + "/confirm", null, BookingView.class);
    return id;
  }

  private String createQuote() {
    AccountResponse account =
        restTemplate
            .postForEntity(
                "/api/accounts",
                new CreateAccountRequest(
                    LegalType.CNPJ, "12345678000195", "Agência Sol e Mar", null, null),
                AccountResponse.class)
            .getBody();
    assertThat(account).isNotNull();
    restTemplate.postForEntity(
        "/api/exchange/pinned-rates",
        new PinRateRequest("USD/BRL", new BigDecimal("5.40"), null, null),
        Void.class);
    QuoteView quote =
        restTemplate
            .postForEntity(
                "/api/quotes",
                new ComposeQuoteRequest(
                    account.id(),
                    Money.of(new BigDecimal("500.00"), "USD"),
                    "USD/BRL",
                    new BigDecimal("0.15"),
                    new BigDecimal("0.10"),
                    null),
                QuoteView.class)
            .getBody();
    assertThat(quote).isNotNull();
    return quote.id().toString();
  }
}
