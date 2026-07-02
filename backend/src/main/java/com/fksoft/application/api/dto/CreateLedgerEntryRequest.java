package com.fksoft.application.api.dto;

import com.fksoft.domain.finance.LedgerDirection;
import com.fksoft.domain.money.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/finance/entries} (SPEC-0015): a new AP/AR ledger entry. The
 * amount is kept in its original currency (DL-0013); the period is {@code YYYY-MM}. The {@code
 * entryType} and {@code party.type} are cadastro codes (was {@code EntryType}/{@code PartyType};
 * SPEC-0031/DL-0118) validated against the cadastro on write (422 on an unknown/inactive code) —
 * the JSON contract is unchanged (they were enum strings, they stay strings).
 *
 * @param direction PAYABLE or RECEIVABLE (required)
 * @param party the counterparty (required)
 * @param amount the amount in its original currency (required)
 * @param entryType the business type — the Compliance key (required; entry-type cadastro code)
 * @param period the target period {@code YYYY-MM} (required)
 */
public record CreateLedgerEntryRequest(
    @NotNull LedgerDirection direction,
    @NotNull @Valid PartyRequest party,
    @NotNull Money amount,
    @NotBlank String entryType,
    @NotBlank String period) {

  /**
   * Counterparty part of the request.
   *
   * @param id the counterparty id (required)
   * @param type the counterparty kind (required; party-type cadastro code)
   */
  public record PartyRequest(@NotBlank String id, @NotBlank String type) {}
}
