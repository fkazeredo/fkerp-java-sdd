package com.fksoft.domain.finance;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a ledger entry (SPEC-0015): the direction, party, amount (in its original
 * currency — DL-0013), business type, period, status and the optional document reference (value).
 *
 * @param id the entry id
 * @param direction PAYABLE or RECEIVABLE
 * @param party the counterparty
 * @param amount the amount in its original currency
 * @param entryType the business type
 * @param period the period ({@code YYYY-MM})
 * @param status the lifecycle status
 * @param documentRef the attached document id, or {@code null}
 * @param createdAt when it was created
 */
public record LedgerEntryView(
    UUID id,
    LedgerDirection direction,
    Party party,
    Money amount,
    EntryType entryType,
    String period,
    EntryStatus status,
    UUID documentRef,
    Instant createdAt) {}
