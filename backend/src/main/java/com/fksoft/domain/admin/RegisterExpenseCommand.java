package com.fksoft.domain.admin;

import com.fksoft.domain.money.Money;
import java.util.UUID;

/**
 * Command to register a recurring administrative expense (SPEC-0025 BR3). Creating it posts a
 * PAYABLE ledger entry in the Finance and points at the documents the Compliance requires.
 *
 * @param supplierId the administrative supplier the expense belongs to (required)
 * @param period the accounting period ({@code YYYY-MM}, required)
 * @param amount the expense amount (Money, required)
 * @param kind the expense-kind cadastro code (required) — maps to the Finance entry type (DL-0085)
 */
public record RegisterExpenseCommand(
    UUID supplierId, String period, Money amount, String kind) {}
