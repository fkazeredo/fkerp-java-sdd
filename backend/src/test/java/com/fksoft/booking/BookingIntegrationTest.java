package com.fksoft.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.application.api.dto.AccountResponse;
import com.fksoft.application.api.dto.CancelBookingRequest;
import com.fksoft.application.api.dto.ComposeQuoteRequest;
import com.fksoft.application.api.dto.CreateAccountRequest;
import com.fksoft.application.api.dto.CreateBookingRequest;
import com.fksoft.application.api.dto.CreateBookingRequest.LocatorRequest;
import com.fksoft.application.api.dto.PinRateRequest;
import com.fksoft.domain.accounts.LegalType;
import com.fksoft.domain.booking.BookingService;
import com.fksoft.domain.booking.BookingStatus;
import com.fksoft.domain.booking.BookingView;
import com.fksoft.domain.booking.CancellationResult;
import com.fksoft.domain.booking.CancellationTypeCodes;
import com.fksoft.domain.booking.LocatorOrigin;
import com.fksoft.domain.money.Money;
import com.fksoft.domain.quoting.QuoteView;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for bookings (SPEC-0006): creation from a quote in ORDERED, internal-locator
 * generation, the lifecycle to COMPLETED, invalid transitions (409), duplicate locator (409),
 * cancellation, missing quote (404), and the 72h PENDING timeout sweep.
 */
class BookingIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private BookingService bookingService;

  @AfterEach
  void cleanUp() {
    // Confirming a booking opens a reconciliation case (via the in-process listener), so clean it
    // here too — otherwise leftover cases pollute the reconciliation tests. Likewise, cancelling a
    // booking now posts AP/AR ledger entries (SPEC-0015 BR5, DL-0041) — clear them too.
    jdbcTemplate.execute("DELETE FROM posted_event_entries");
    jdbcTemplate.execute("DELETE FROM ledger_entries");
    jdbcTemplate.execute("DELETE FROM reconciliation_cases");
    jdbcTemplate.execute("DELETE FROM bookings");
    jdbcTemplate.execute("DELETE FROM override_records");
    jdbcTemplate.execute("DELETE FROM quotes");
    jdbcTemplate.execute("DELETE FROM accounts");
    jdbcTemplate.execute("DELETE FROM pinned_sell_rates");
  }

  @Test
  void createsBookingInOrderedFromQuoteWithExternalLocator() {
    UUID quoteId = createQuote();

    ResponseEntity<BookingView> response =
        restTemplate.postForEntity(
            "/api/bookings", externalBooking(quoteId, "ALAMO-7731QX"), BookingView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    BookingView booking = response.getBody();
    assertThat(booking).isNotNull();
    assertThat(booking.status()).isEqualTo(BookingStatus.ORDERED);
    assertThat(booking.quoteId()).isEqualTo(quoteId);
    assertThat(booking.accountId()).isNotNull();
    assertThat(booking.locator().origin()).isEqualTo(LocatorOrigin.EXTERNAL);
    assertThat(booking.locator().code()).isEqualTo("ALAMO-7731QX");
  }

  @Test
  void generatesInternalLocator() {
    UUID quoteId = createQuote();

    BookingView booking =
        restTemplate
            .postForEntity(
                "/api/bookings",
                new CreateBookingRequest(
                    quoteId, new LocatorRequest(LocatorOrigin.INTERNAL, null), null),
                BookingView.class)
            .getBody();

    assertThat(booking).isNotNull();
    assertThat(booking.locator().origin()).isEqualTo(LocatorOrigin.INTERNAL);
    assertThat(booking.locator().code()).startsWith("INT-");
  }

  @Test
  void runsLifecycleToCompletedAndRejectsConfirmingACompletedBooking() {
    UUID bookingId = createBooking();

    transition(bookingId, "pending");
    BookingView confirmed = transition(bookingId, "confirm").getBody();
    assertThat(confirmed).isNotNull();
    assertThat(confirmed.status()).isEqualTo(BookingStatus.CONFIRMED);
    assertThat(confirmed.confirmedAt()).isNotNull();

    BookingView completed = transition(bookingId, "complete").getBody();
    assertThat(completed).isNotNull();
    assertThat(completed.status()).isEqualTo(BookingStatus.COMPLETED);

    ResponseEntity<ApiErrorResponse> invalid =
        restTemplate.postForEntity(
            "/api/bookings/" + bookingId + "/confirm", null, ApiErrorResponse.class);
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(invalid.getBody()).isNotNull();
    assertThat(invalid.getBody().code()).isEqualTo("booking.transition.invalid");
  }

  @Test
  void rejectsInvalidTransitionFromOrdered() {
    UUID bookingId = createBooking();

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/bookings/" + bookingId + "/confirm", null, ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("booking.transition.invalid");
  }

  @Test
  void cancelsWithReasonAndReturnsTheChargesEnvelope() {
    UUID bookingId = createBooking();
    transition(bookingId, "pending");

    ResponseEntity<CancellationResult> response =
        restTemplate.postForEntity(
            "/api/bookings/" + bookingId + "/cancel",
            new CancelBookingRequest("cliente desistiu", null, null),
            CancellationResult.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(BookingStatus.CANCELLED);
    // A booking cancelled from PENDING (never confirmed, no administered policy) has the safe
    // default
    // STANDARD policy with no windows -> no charges.
    assertThat(response.getBody().policyType()).isEqualTo(CancellationTypeCodes.STANDARD);
    assertThat(response.getBody().charges()).isEmpty();

    BookingView reloaded =
        restTemplate.getForEntity("/api/bookings/" + bookingId, BookingView.class).getBody();
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.cancelReason()).isEqualTo("cliente desistiu");
  }

  @Test
  void rejectsBookingForMissingQuote() {
    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/bookings", externalBooking(UUID.randomUUID(), "X-1"), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("booking.quote.not-found");
  }

  @Test
  void rejectsDuplicateLocator() {
    UUID quoteId = createQuote();
    restTemplate.postForEntity(
        "/api/bookings", externalBooking(quoteId, "DUP-1"), BookingView.class);

    ResponseEntity<ApiErrorResponse> duplicate =
        restTemplate.postForEntity(
            "/api/bookings", externalBooking(quoteId, "DUP-1"), ApiErrorResponse.class);

    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(duplicate.getBody()).isNotNull();
    assertThat(duplicate.getBody().code()).isEqualTo("booking.locator.duplicate");
  }

  @Test
  void rejectsBlankExternalLocator() {
    UUID quoteId = createQuote();

    ResponseEntity<ApiErrorResponse> response =
        restTemplate.postForEntity(
            "/api/bookings", externalBooking(quoteId, "   "), ApiErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("booking.locator.invalid");
  }

  @Test
  void expiresPendingBookingsPastTheTimeout() {
    UUID bookingId = createBooking();
    transition(bookingId, "pending");

    int expired = bookingService.expirePendingBookings(Instant.now().plus(Duration.ofHours(73)));

    assertThat(expired).isEqualTo(1);
    BookingView reloaded =
        restTemplate.getForEntity("/api/bookings/" + bookingId, BookingView.class).getBody();
    assertThat(reloaded).isNotNull();
    assertThat(reloaded.status()).isEqualTo(BookingStatus.CANCELLED);
    assertThat(reloaded.cancelReason()).isEqualTo("PENDING_TIMEOUT");
  }

  private ResponseEntity<BookingView> transition(UUID bookingId, String action) {
    return restTemplate.postForEntity(
        "/api/bookings/" + bookingId + "/" + action, null, BookingView.class);
  }

  private UUID createBooking() {
    UUID quoteId = createQuote();
    BookingView booking =
        restTemplate
            .postForEntity(
                "/api/bookings",
                externalBooking(quoteId, "LOC-" + UUID.randomUUID()),
                BookingView.class)
            .getBody();
    assertThat(booking).isNotNull();
    return booking.id();
  }

  private UUID createQuote() {
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
    return quote.id();
  }

  private static CreateBookingRequest externalBooking(UUID quoteId, String code) {
    return new CreateBookingRequest(
        quoteId, new LocatorRequest(LocatorOrigin.EXTERNAL, code), null);
  }
}
