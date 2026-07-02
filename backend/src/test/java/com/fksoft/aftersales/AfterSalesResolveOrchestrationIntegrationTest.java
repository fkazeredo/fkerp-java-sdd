package com.fksoft.aftersales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.CancellationPolicyRequest;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.CreateBookingRequest;
import com.fksoft.application.api.dto.CreateBookingRequest.LocatorRequest;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.aftersales.AfterSalesService;
import com.fksoft.domain.aftersales.CaseResolutionCodes;
import com.fksoft.domain.aftersales.OpenCaseCommand;
import com.fksoft.domain.aftersales.ResolveCaseCommand;
import com.fksoft.domain.aftersales.SupportCaseRefundDuplicateException;
import com.fksoft.domain.aftersales.SupportCaseStatus;
import com.fksoft.domain.aftersales.SupportCaseTypeCodes;
import com.fksoft.domain.aftersales.SupportCaseView;
import com.fksoft.domain.booking.BookingView;
import com.fksoft.domain.booking.CancellationTypeCodes;
import com.fksoft.domain.booking.CostBearer;
import com.fksoft.domain.booking.LocatorOrigin;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.payout.PayoutKindCodes;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the AfterSales resolution orchestration (SPEC-0018 BR2/BR3/BR5/BR6;
 * DL-0054):
 *
 * <ul>
 *   <li>a {@code REFUND_REQUEST} resolved as {@code REFUND_APPROVED} triggers a Payout {@code
 *       REFUND} <strong>exactly once</strong> (idempotency guard, BR3), referencing the case as its
 *       origin, and accumulates the cost-to-serve (BR5);
 *   <li>that refund does <strong>NOT</strong> cancel the pre-existing supplier obligation — the
 *       <strong>merchant trap stays intact</strong> (DL-0024/DL-0051): the supplier charge
 *       survives;
 *   <li>a {@code CANCELLATION_REQUEST} resolved as {@code CANCEL_APPROVED} drives the Booking
 *       cancellation (SPEC-0010), which AfterSales never does itself (BR2).
 * </ul>
 *
 * The booking fixture is built through the REST API (the proven path, like {@code
 * MerchantTrapIntegrationTest}); the AfterSales case is driven through the {@link
 * AfterSalesService} facade, which calls the real Payout/Booking facades. Runs against a real
 * Postgres (Testcontainers).
 */
class AfterSalesResolveOrchestrationIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private AfterSalesService afterSalesService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM support_cases");
    jdbcTemplate.execute("DELETE FROM payout_installments");
    jdbcTemplate.execute("DELETE FROM payouts");
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
  void resolvingARefundCaseTriggersExactlyOnePayoutAndKeepsTheSupplierObligation() {
    String scope = "PORTAL-EXP-AFTERSALES";
    UUID bookingId = confirmedMerchantBooking(scope);

    // A pre-existing supplier obligation: cancel the ALL_SALES_FINAL booking so the supplier charge
    // (the merchant obligation that must NOT vanish when the customer is refunded) already exists.
    restTemplate.postForEntity(
        "/api/bookings/" + bookingId + "/cancel",
        new com.fksoft.application.api.dto.CancelBookingRequest(
            "MERCHANT_CANCEL", java.time.Instant.now().plus(java.time.Duration.ofHours(2)), null),
        Object.class);
    long supplierChargesBefore = countSupplierCharges(bookingId);
    assertThat(supplierChargesBefore).isEqualTo(1);

    // Open a refund case and approve it → triggers a Payout REFUND referencing the case.
    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand(
                bookingId.toString(), SupportCaseTypeCodes.REFUND_REQUEST, "voo cancelado"),
            "agent");
    SupportCaseView resolved =
        afterSalesService.resolve(
            opened.id(),
            new ResolveCaseCommand(
                CaseResolutionCodes.REFUND_APPROVED,
                Money.of(new BigDecimal("480.00"), "BRL"),
                Money.of(new BigDecimal("12.00"), "BRL"),
                null,
                null),
            "agent");

    assertThat(resolved.status()).isEqualTo(SupportCaseStatus.RESOLVED);
    assertThat(resolved.linkedPayoutId()).isNotNull();
    // Cost-to-serve = handling 12 + refund 480 = 492 (BR5).
    assertThat(resolved.costToServeTotal()).isEqualTo(Money.of(new BigDecimal("492.00"), "BRL"));

    // Exactly ONE Payout REFUND was created, referencing the case as its origin (BR3, idempotent).
    assertThat(countRefundPayouts(opened.id())).isEqualTo(1);

    // The supplier obligation is STILL there, untouched — the merchant trap holds
    // (DL-0024/DL-0051).
    assertThat(countSupplierCharges(bookingId)).isEqualTo(supplierChargesBefore);

    // Idempotency: a second refund approval does NOT create a second Payout (BR3).
    assertThatThrownBy(
            () ->
                afterSalesService.resolve(
                    opened.id(),
                    new ResolveCaseCommand(
                        CaseResolutionCodes.REFUND_APPROVED,
                        Money.of(new BigDecimal("480.00"), "BRL"),
                        null,
                        null,
                        null),
                    "agent"))
        .isInstanceOf(SupportCaseRefundDuplicateException.class);
    assertThat(countRefundPayouts(opened.id())).isEqualTo(1);
  }

  @Test
  void resolvingACancellationCaseDrivesTheBookingCancellation() {
    String scope = "PORTAL-EXP-CANCEL";
    UUID bookingId = confirmedMerchantBooking(scope);

    SupportCaseView opened =
        afterSalesService.open(
            new OpenCaseCommand(
                bookingId.toString(),
                SupportCaseTypeCodes.CANCELLATION_REQUEST,
                "cliente desistiu"),
            "agent");

    SupportCaseView resolved =
        afterSalesService.resolve(
            opened.id(),
            new ResolveCaseCommand(
                CaseResolutionCodes.CANCEL_APPROVED,
                null,
                null,
                java.time.Instant.now().plus(java.time.Duration.ofHours(5)),
                "AFTERSALES_CANCEL"),
            "agent");

    assertThat(resolved.status()).isEqualTo(SupportCaseStatus.RESOLVED);
    // AfterSales did NOT change the booking itself — the Booking module did (BR2): it is CANCELLED.
    BookingView booking =
        restTemplate.getForEntity("/api/bookings/" + bookingId, BookingView.class).getBody();
    assertThat(booking).isNotNull();
    assertThat(booking.status().name()).isEqualTo("CANCELLED");
  }

  private long countSupplierCharges(UUID bookingId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM cancellation_charges WHERE booking_id = ? AND kind = 'SUPPLIER'",
            Integer.class,
            bookingId);
    return count == null ? 0 : count;
  }

  private long countRefundPayouts(UUID caseId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM payouts WHERE kind = ? AND origin_ref = ?",
            Integer.class,
            PayoutKindCodes.REFUND,
            caseId.toString());
    return count == null ? 0 : count;
  }

  /** Builds a CONFIRMED booking under an ALL_SALES_FINAL merchant policy (supplier obligation). */
  private UUID confirmedMerchantBooking(String scope) {
    restTemplate.put(
        "/api/products/" + scope + "/cancellation-policy",
        new CancellationPolicyRequest(
            CancellationTypeCodes.ALL_SALES_FINAL,
            List.of(),
            false,
            CostBearer.SUPPLIER,
            true,
            null,
            false));

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

    BookingView booking =
        restTemplate
            .postForEntity(
                "/api/bookings",
                new CreateBookingRequest(
                    quote.id(), new LocatorRequest(LocatorOrigin.EXTERNAL, "LOC-" + scope), scope),
                BookingView.class)
            .getBody();
    assertThat(booking).isNotNull();
    restTemplate.postForEntity(
        "/api/bookings/" + booking.id() + "/pending", null, BookingView.class);
    restTemplate.postForEntity(
        "/api/bookings/" + booking.id() + "/confirm", null, BookingView.class);
    return booking.id();
  }
}
