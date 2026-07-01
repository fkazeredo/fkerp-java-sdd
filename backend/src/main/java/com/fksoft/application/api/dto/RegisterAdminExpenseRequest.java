package com.fksoft.application.api.dto;

import com.fksoft.domain.admin.RegisterExpenseCommand;
import com.fksoft.domain.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/admin/expenses} (SPEC-0025 BR3): a recurring administrative
 * expense that creates a PAYABLE entry in the Finance ledger and points at the required documents.
 *
 * @param supplierId the administrative supplier (required)
 * @param period the accounting period {@code YYYY-MM} (required)
 * @param amount the expense amount (required)
 * @param kind the expense-kind cadastro code (required) — maps to the Finance entry type (DL-0085)
 */
public record RegisterAdminExpenseRequest(
    @NotNull UUID supplierId,
    @NotBlank String period,
    @NotNull Money amount,
    @NotBlank String kind) {

  /** Translates this request to the domain command. */
  public RegisterExpenseCommand toCommand() {
    return new RegisterExpenseCommand(supplierId, period, amount, kind);
  }
}
