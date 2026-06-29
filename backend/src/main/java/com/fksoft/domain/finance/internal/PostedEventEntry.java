package com.fksoft.domain.finance.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Idempotency record for event-driven AP/AR posting (SPEC-0015 BR5, DL-0041): one row per posted
 * business fact, keyed by the unique {@code (sourceRef, chargeKind)}. It is written in the same
 * transaction as the {@link LedgerEntry} it tracks, so a re-delivered source event is a no-op (the
 * existence pre-check finds it, or the UNIQUE constraint rejects a concurrent double-post). The
 * {@code sourceRef} is a value reference (the booking id, as text) — no cross-module FK.
 * Module-internal.
 */
@Entity
@Table(name = "posted_event_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostedEventEntry {

  @Id private UUID id;

  private String sourceRef;

  private String chargeKind;

  private UUID entryId;

  private Instant createdAt;

  /**
   * Records that the fact {@code (sourceRef, chargeKind)} was posted as ledger entry {@code
   * entryId}.
   *
   * @param sourceRef the source fact reference (e.g. the booking id, as text)
   * @param chargeKind the posted charge kind (value)
   * @param entryId the ledger entry it produced
   * @param now the instant it was posted (UTC)
   * @return a new, persistable idempotency record
   */
  public static PostedEventEntry of(
      String sourceRef, String chargeKind, UUID entryId, Instant now) {
    PostedEventEntry posted = new PostedEventEntry();
    posted.id = UUID.randomUUID();
    posted.sourceRef = sourceRef;
    posted.chargeKind = chargeKind;
    posted.entryId = entryId;
    posted.createdAt = now;
    return posted;
  }
}
