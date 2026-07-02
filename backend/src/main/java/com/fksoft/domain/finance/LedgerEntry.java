package com.fksoft.domain.finance;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
// EntryType/PartyType became editable cadastros (SPEC-0031/DL-0118): stored as String codes.
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ledger entry aggregate (SPEC-0015): a "we owe / we are owed" record with a business type, a
 * period, an amount in its original currency (DL-0013) and a lifecycle (PROVISIONAL→CONFIRMED→
 * SETTLED, BR2). It carries an optional {@code documentRef} (value) once a document is attached.
 * The document <em>rule</em> is never enforced here — that is the Compliance's job (BR6).
 * Module-internal.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class LedgerEntry {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private LedgerDirection direction;

  private String partyId;

  /** The party-type cadastro code (was {@code PartyType}; SPEC-0031/DL-0118). */
  private String partyType;

  private BigDecimal amount;

  private String currency;

  /** The entry-type cadastro code (was {@code EntryType}; SPEC-0031/DL-0118). */
  private String entryType;

  private String period;

  @Enumerated(EnumType.STRING)
  private EntryStatus status;

  private UUID documentRef;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Registers a new entry in {@link EntryStatus#PROVISIONAL} (BR2). The amount is stored in its
   * original currency (DL-0013).
   *
   * @param direction PAYABLE or RECEIVABLE
   * @param party the counterparty
   * @param amount the amount (original currency)
   * @param entryType the business type (entry-type cadastro code)
   * @param period the period ({@code YYYY-MM})
   * @param now creation instant (UTC)
   * @param actor who registered it (audit)
   * @return a new, persistable entry
   */
  public static LedgerEntry register(
      LedgerDirection direction,
      Party party,
      Money amount,
      String entryType,
      String period,
      Instant now,
      String actor) {
    LedgerEntry entry = new LedgerEntry();
    entry.id = UUID.randomUUID();
    entry.direction = direction;
    entry.partyId = party.id();
    entry.partyType = party.type();
    entry.amount = amount.amount();
    entry.currency = amount.currency();
    entry.entryType = entryType;
    entry.period = period;
    entry.status = EntryStatus.PROVISIONAL;
    entry.createdAt = now;
    entry.updatedAt = now;
    entry.createdBy = actor;
    entry.updatedBy = actor;
    return entry;
  }

  /**
   * Moves the entry to {@code target} if the lifecycle allows it (BR2).
   *
   * @param target the target status
   * @param now the transition instant (UTC)
   * @param actor who performed it (audit)
   * @throws FinanceEntryTransitionInvalidException when the transition is not allowed (BR2)
   */
  public void transitionTo(EntryStatus target, Instant now, String actor) {
    if (!status.canTransitionTo(target)) {
      throw new FinanceEntryTransitionInvalidException();
    }
    status = target;
    updatedAt = now;
    updatedBy = actor;
  }

  /** Records the attached document reference (value), set by the Compliance flow. */
  public void attachDocument(UUID documentId, Instant now, String actor) {
    documentRef = documentId;
    updatedAt = now;
    updatedBy = actor;
  }

  /** The entry id. */
  public UUID id() {
    return id;
  }

  /** The amount as a money value (original currency). */
  public Money money() {
    return Money.of(amount, currency);
  }

  /** Projects the aggregate to its public read view. */
  public LedgerEntryView toView() {
    return new LedgerEntryView(
        id,
        direction,
        new Party(partyId, partyType),
        Money.of(amount, currency),
        entryType,
        period,
        status,
        documentRef,
        createdAt);
  }
}
