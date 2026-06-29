package com.fksoft.domain.finance;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a ledger entry was registered (SPEC-0015 Events). Published in-process; consumed
 * by Compliance (to track the entry's conformance for the close-check) and Intelligence.
 *
 * @param entryId the new entry id
 * @param direction PAYABLE or RECEIVABLE
 * @param entryType the entry's business type (value)
 * @param period the period it belongs to ({@code YYYY-MM})
 * @param occurredAt when it was registered
 */
public record LedgerEntryRegistered(
    UUID entryId, LedgerDirection direction, String entryType, String period, Instant occurredAt) {}
