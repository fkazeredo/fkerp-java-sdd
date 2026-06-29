package com.fksoft.domain.finance;

import java.util.UUID;

/**
 * Frozen, document-relevant view of a ledger entry exposed to the Compliance through {@link
 * LedgerDirectory}: the id, the business type (value) and the period — exactly what the close-check
 * needs to decide which documents are mandatory (SPEC-0008 BR6). Carries no entity and no money.
 *
 * @param entryId the ledger entry id
 * @param entryType the entry's business type (value)
 * @param period the period it belongs to ({@code YYYY-MM})
 */
public record LedgerEntrySnapshot(UUID entryId, String entryType, String period) {}
