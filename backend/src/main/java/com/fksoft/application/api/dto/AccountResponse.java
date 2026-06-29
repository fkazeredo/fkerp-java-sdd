package com.fksoft.application.api.dto;

import com.fksoft.domain.accounts.AccountStatus;
import com.fksoft.domain.accounts.AccountView;
import com.fksoft.domain.accounts.LegalType;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for account endpoints. Built from the domain {@link AccountView}, so the entity
 * never crosses the delivery boundary.
 */
public record AccountResponse(
    UUID id,
    LegalType legalType,
    String documentNumber,
    String displayName,
    AccountStatus status,
    String cadastur,
    String iata,
    Instant createdAt) {

  /** Maps a domain view to the response DTO. */
  public static AccountResponse from(AccountView view) {
    return new AccountResponse(
        view.id(),
        view.legalType(),
        view.documentNumber(),
        view.displayName(),
        view.status(),
        view.cadastur(),
        view.iata(),
        view.createdAt());
  }
}
