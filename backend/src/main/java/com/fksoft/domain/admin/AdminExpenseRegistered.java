package com.fksoft.domain.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: a recurring administrative expense was registered and its Finance ledger entry was
 * created (SPEC-0025 Events). In-process; consumed by {@code compliance} (tracks the required
 * document) and {@code intelligence} (fixed cost). Carries the created Finance entry id and the
 * entry type (values) — no personal data.
 *
 * @param expenseId the administrative expense id
 * @param financeEntryId the created Finance ledger entry id (value)
 * @param entryType the Finance entry-type cadastro code the expense posted as (SPEC-0031/DL-0118)
 * @param occurredAt when it was registered
 */
public record AdminExpenseRegistered(
    UUID expenseId, UUID financeEntryId, String entryType, Instant occurredAt) {}
