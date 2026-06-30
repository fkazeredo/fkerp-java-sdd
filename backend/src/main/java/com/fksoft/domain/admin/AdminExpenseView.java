package com.fksoft.domain.admin;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public read view of a recurring administrative expense (SPEC-0025). Besides the persisted fields
 * it carries the {@code requiredDocuments} the Compliance demands for the posted Finance entry
 * (read from the {@code DocumentRequirementDirectory}, DL-0086) — what the operator must attach for
 * the month to close.
 *
 * @param id the expense id
 * @param supplierId the supplier the expense belongs to
 * @param period the accounting period ({@code YYYY-MM})
 * @param amount the expense amount (Money)
 * @param kind the expense kind
 * @param financeEntryId the created Finance ledger entry id (value)
 * @param requiredDocuments the document types required at registration (Compliance), may be empty
 * @param createdAt when it was registered
 */
public record AdminExpenseView(
    UUID id,
    UUID supplierId,
    String period,
    Money amount,
    AdminExpenseKind kind,
    UUID financeEntryId,
    List<String> requiredDocuments,
    Instant createdAt) {}
