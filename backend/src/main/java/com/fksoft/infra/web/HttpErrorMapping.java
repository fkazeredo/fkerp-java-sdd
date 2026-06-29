package com.fksoft.infra.web;

import static java.util.Map.entry;

import com.fksoft.domain.accounts.AccountDocumentDuplicateException;
import com.fksoft.domain.accounts.AccountDocumentInvalidException;
import com.fksoft.domain.accounts.AccountNotFoundException;
import com.fksoft.domain.booking.BookingLocatorDuplicateException;
import com.fksoft.domain.booking.BookingLocatorInvalidException;
import com.fksoft.domain.booking.BookingNotFoundException;
import com.fksoft.domain.booking.BookingQuoteNotFoundException;
import com.fksoft.domain.booking.BookingTransitionInvalidException;
import com.fksoft.domain.commissioning.CommissionBaseInvalidException;
import com.fksoft.domain.commissioning.CommissionPctInvalidException;
import com.fksoft.domain.error.DomainException;
import com.fksoft.domain.exchange.ExchangeCurrencyPairInvalidException;
import com.fksoft.domain.exchange.ExchangeRateInvalidException;
import com.fksoft.domain.exchange.ExchangeRateNotFoundException;
import com.fksoft.domain.finance.FinanceEntryNotFoundException;
import com.fksoft.domain.finance.FinanceEntryTransitionInvalidException;
import com.fksoft.domain.finance.FinancePartyInvalidException;
import com.fksoft.domain.finance.FinancePeriodCannotCloseException;
import com.fksoft.domain.finance.FinancePeriodClosedException;
import com.fksoft.domain.finance.FinancePeriodInvalidException;
import com.fksoft.domain.quoting.QuoteAccountNotFoundException;
import com.fksoft.domain.quoting.QuoteNotFoundException;
import com.fksoft.domain.quoting.QuoteOverrideCurrencyMismatchException;
import com.fksoft.domain.quoting.QuoteOverrideReasonRequiredException;
import com.fksoft.domain.quoting.QuoteRateMissingException;
import com.fksoft.domain.reconciliation.ReconciliationCaseNotFoundException;
import com.fksoft.domain.reconciliation.ReconciliationCurrencyMismatchException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Registry that maps each {@link DomainException} subtype to the HTTP status the presentation layer
 * should return (ADR 0011). Keeping the mapping here — not in the domain — keeps domain exceptions
 * free of transport concerns.
 *
 * <p>Any unmapped {@code DomainException} defaults to {@code 422 Unprocessable Entity}. A
 * build-time test ({@code HttpErrorMappingCompletenessTest}) fails if a {@code DomainException}
 * subtype is ever left unmapped, so the default can never hide a forgotten entry.
 */
@Component
public class HttpErrorMapping {

  private final Map<Class<? extends DomainException>, HttpStatus> mapping =
      Map.ofEntries(
          entry(AccountDocumentInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(AccountDocumentDuplicateException.class, HttpStatus.CONFLICT),
          entry(AccountNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(ExchangeCurrencyPairInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(ExchangeRateInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(ExchangeRateNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(CommissionPctInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(CommissionBaseInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(QuoteAccountNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(QuoteRateMissingException.class, HttpStatus.UNPROCESSABLE_ENTITY),
          entry(QuoteNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(QuoteOverrideReasonRequiredException.class, HttpStatus.BAD_REQUEST),
          entry(QuoteOverrideCurrencyMismatchException.class, HttpStatus.BAD_REQUEST),
          entry(BookingQuoteNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(BookingNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(BookingTransitionInvalidException.class, HttpStatus.CONFLICT),
          entry(BookingLocatorDuplicateException.class, HttpStatus.CONFLICT),
          entry(BookingLocatorInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(ReconciliationCaseNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(ReconciliationCurrencyMismatchException.class, HttpStatus.BAD_REQUEST),
          entry(FinancePartyInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(FinancePeriodInvalidException.class, HttpStatus.BAD_REQUEST),
          entry(FinanceEntryNotFoundException.class, HttpStatus.NOT_FOUND),
          entry(FinanceEntryTransitionInvalidException.class, HttpStatus.CONFLICT),
          entry(FinancePeriodClosedException.class, HttpStatus.CONFLICT),
          entry(FinancePeriodCannotCloseException.class, HttpStatus.CONFLICT));

  /** The HTTP status for a domain exception type; {@code 422} when unmapped. */
  public HttpStatus statusFor(Class<? extends DomainException> type) {
    return mapping.getOrDefault(type, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  /** The set of explicitly mapped exception types (used by the completeness test). */
  public Set<Class<? extends DomainException>> mappedTypes() {
    return mapping.keySet();
  }
}
