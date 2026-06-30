package com.fksoft.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.CancelBookingRequest;
import com.fksoft.application.api.dto.CancellationPolicyRequest;
import com.fksoft.application.api.dto.CancellationPolicyRequest.WindowRequest;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.CreateBookingRequest;
import com.fksoft.application.api.dto.CreateBookingRequest.LocatorRequest;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.booking.BookingView;
import com.fksoft.domain.booking.CancellationResult;
import com.fksoft.domain.booking.CancellationType;
import com.fksoft.domain.booking.ChargeKind;
import com.fksoft.domain.booking.CostBearer;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the rich cancellation (SPEC-0010): a STANDARD cancellation within a 50%
 * window charges the right penalty with the right cost bearer; and — the keystone <strong>merchant
 * trap regression</strong> — an ALL_SALES_FINAL merchant sale cancelled with a commercial refund
 * records BOTH a supplier charge AND a customer refund that do NOT net out (the supplier obligation
 * survives the refund). Both the response envelope and the persisted {@code cancellation_charges}
 * rows are asserted.
 */
class MerchantTrapIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    // Finance now posts AP/AR ledger entries from the cancellation events (SPEC-0015 BR5, DL-0041);
    // clear them so a cancelled-booking test does not leak entries into period-keyed Finance tests.
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
  void standardCancellationInsideA50PercentWindowChargesTheCorrectPenaltyAndCostBearer() {
    String scope = "CAR-ALAMO-STD";
    putPolicy(
        scope,
        new CancellationPolicyRequest(
            CancellationType.STANDARD,
            List.of(new WindowRequest(24, new BigDecimal("0.50"))),
            true,
            CostBearer.AGENCY,
            false,
            null,
            false));
    String bookingId = confirmedBooking(scope);

    // Service starts in ~10h (within the 24h / 50% window). Sale (baseConverted) = 500 * 5.40 =
    // 2700.
    CancellationResult result =
        restTemplate
            .postForEntity(
                "/api/bookings/" + bookingId + "/cancel",
                new CancelBookingRequest(
                    "CLIENT_REQUEST",
                    java.time.Instant.now().plus(java.time.Duration.ofHours(10)),
                    null),
                CancellationResult.class)
            .getBody();

    assertThat(result).isNotNull();
    assertThat(result.policyType()).isEqualTo(CancellationType.STANDARD);
    assertThat(result.charges()).hasSize(1);
    assertThat(result.charges().get(0).kind()).isEqualTo(ChargeKind.PENALTY);
    assertThat(result.charges().get(0).amount())
        .isEqualTo(Money.of(new BigDecimal("1350.00"), "BRL"));
    assertThat(result.charges().get(0).costBearer()).isEqualTo(CostBearer.AGENCY);
  }

  @Test
  void merchantAllSalesFinalCancellationWithRefundRecordsTwoObligationsThatDoNotNetOut() {
    String scope = "PORTAL-EXP-TOUR-99";
    // Merchant of record, ALL_SALES_FINAL (the Portal de Experiências trap).
    putPolicy(
        scope,
        new CancellationPolicyRequest(
            CancellationType.ALL_SALES_FINAL,
            List.of(),
            false,
            CostBearer.SUPPLIER,
            true,
            null,
            false));
    String bookingId = confirmedBooking(scope);

    // Commercial decision: refund the customer 2700 BRL, even though it is ALL_SALES_FINAL.
    CancellationResult result =
        restTemplate
            .postForEntity(
                "/api/bookings/" + bookingId + "/cancel",
                new CancelBookingRequest(
                    "CLIENT_REQUEST",
                    java.time.Instant.now().plus(java.time.Duration.ofHours(2)),
                    Money.of(new BigDecimal("2700.00"), "BRL")),
                CancellationResult.class)
            .getBody();

    assertThat(result).isNotNull();
    assertThat(result.policyType()).isEqualTo(CancellationType.ALL_SALES_FINAL);

    // THE TRAP: TWO distinct obligations — supplier cost AND customer refund — that do NOT net out.
    assertThat(result.charges()).hasSize(2);
    assertThat(result.charges())
        .anySatisfy(
            c -> {
              assertThat(c.kind()).isEqualTo(ChargeKind.SUPPLIER);
              // Supplier cost (basePrice = 500 USD) is due IN FULL, not 500 - refund.
              assertThat(c.amount()).isEqualTo(Money.of(new BigDecimal("500.00"), "USD"));
              assertThat(c.costBearer()).isEqualTo(CostBearer.ACME); // merchant of record -> Acme
            })
        .anySatisfy(
            c -> {
              assertThat(c.kind()).isEqualTo(ChargeKind.CUSTOMER_REFUND);
              assertThat(c.amount()).isEqualTo(Money.of(new BigDecimal("2700.00"), "BRL"));
              assertThat(c.costBearer()).isEqualTo(CostBearer.ACME);
            });

    // And the persistence proves it: two rows, the supplier obligation present despite the refund.
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT kind, amount, currency, cost_bearer FROM cancellation_charges "
                + "WHERE booking_id = ? ORDER BY kind",
            java.util.UUID.fromString(bookingId));
    assertThat(rows).hasSize(2);
    assertThat(rows)
        .anySatisfy(r -> assertThat(r.get("kind")).isEqualTo("SUPPLIER"))
        .anySatisfy(r -> assertThat(r.get("kind")).isEqualTo("CUSTOMER_REFUND"));
    // The two amounts were NOT combined into a single net figure.
    BigDecimal supplier =
        rows.stream()
            .filter(r -> "SUPPLIER".equals(r.get("kind")))
            .map(r -> (BigDecimal) r.get("amount"))
            .findFirst()
            .orElseThrow();
    assertThat(supplier).isEqualByComparingTo("500.00");
  }

  private void putPolicy(String scope, CancellationPolicyRequest request) {
    restTemplate.put("/api/products/" + scope + "/cancellation-policy", request);
  }

  /**
   * Creates a booking for the scope, then drives it ORDERED -> PENDING -> CONFIRMED (freezes
   * snapshot).
   */
  private String confirmedBooking(String scope) {
    String quoteId = createQuote();
    BookingView booking =
        restTemplate
            .postForEntity(
                "/api/bookings",
                new CreateBookingRequest(
                    java.util.UUID.fromString(quoteId),
                    new LocatorRequest(
                        com.fksoft.domain.booking.LocatorOrigin.EXTERNAL, "LOC-" + scope),
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
