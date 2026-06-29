package com.fksoft.domain.accounts;

import com.fksoft.domain.error.DomainException;
import com.fksoft.domain.error.ErrorDetails;
import java.util.Map;

/**
 * Raised when a document is not structurally valid for its legal type (BR2): wrong digit count or
 * failing check digits. Carries the offending field ({@code documentNumber}) as domain detail; the
 * presentation layer maps it to {@code 400 Bad Request}.
 */
public class AccountDocumentInvalidException extends DomainException implements ErrorDetails {

  public AccountDocumentInvalidException() {
    super("account.document.invalid");
  }

  @Override
  public Map<String, Object> details() {
    return Map.of("documentNumber", "invalid");
  }
}
