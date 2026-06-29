package com.fksoft.infra.web;

import com.fksoft.domain.accounts.AccountDocumentDuplicateException;
import com.fksoft.domain.accounts.AccountDocumentInvalidException;
import com.fksoft.domain.accounts.AccountNotFoundException;
import com.fksoft.domain.commissioning.CommissionBaseInvalidException;
import com.fksoft.domain.commissioning.CommissionPctInvalidException;
import com.fksoft.domain.error.DomainException;
import com.fksoft.domain.exchange.ExchangeCurrencyPairInvalidException;
import com.fksoft.domain.exchange.ExchangeRateInvalidException;
import com.fksoft.domain.exchange.ExchangeRateNotFoundException;
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
      Map.of(
          AccountDocumentInvalidException.class, HttpStatus.BAD_REQUEST,
          AccountDocumentDuplicateException.class, HttpStatus.CONFLICT,
          AccountNotFoundException.class, HttpStatus.NOT_FOUND,
          ExchangeCurrencyPairInvalidException.class, HttpStatus.BAD_REQUEST,
          ExchangeRateInvalidException.class, HttpStatus.BAD_REQUEST,
          ExchangeRateNotFoundException.class, HttpStatus.NOT_FOUND,
          CommissionPctInvalidException.class, HttpStatus.BAD_REQUEST,
          CommissionBaseInvalidException.class, HttpStatus.BAD_REQUEST);

  /** The HTTP status for a domain exception type; {@code 422} when unmapped. */
  public HttpStatus statusFor(Class<? extends DomainException> type) {
    return mapping.getOrDefault(type, HttpStatus.UNPROCESSABLE_ENTITY);
  }

  /** The set of explicitly mapped exception types (used by the completeness test). */
  public Set<Class<? extends DomainException>> mappedTypes() {
    return mapping.keySet();
  }
}
