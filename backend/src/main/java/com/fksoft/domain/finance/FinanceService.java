package com.fksoft.domain.finance;

import com.fksoft.domain.finance.internal.AccountingPeriod;
import com.fksoft.domain.finance.internal.AccountingPeriodRepository;
import com.fksoft.domain.finance.internal.LedgerEntry;
import com.fksoft.domain.finance.internal.LedgerEntryRepository;
import com.fksoft.domain.finance.internal.PostedEventEntry;
import com.fksoft.domain.finance.internal.PostedEventEntryRepository;
import com.fksoft.domain.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Finance module (SPEC-0015): registers AP/AR ledger entries, drives
 * the entry lifecycle, runs the monthly close (consulting the Compliance veto via {@link
 * CloseGuard}), and implements the {@link LedgerDirectory} read port. Finance owns the period lock
 * and calendar; it never imposes the document rule — it only consults the guard and respects the
 * veto (BR6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceService implements LedgerDirectory {

  private final LedgerEntryRepository entries;
  private final AccountingPeriodRepository periods;
  private final PostedEventEntryRepository postedEvents;
  private final CloseGuard closeGuard;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Registers a ledger entry in PROVISIONAL (BR2) against an open period (creating the period
   * lazily OPEN, BR4). Publishes {@code LedgerEntryRegistered}.
   *
   * @param direction PAYABLE or RECEIVABLE
   * @param party the counterparty
   * @param amount the amount in its original currency (DL-0013)
   * @param entryType the business type (the Compliance key)
   * @param periodId the target period
   * @param actor who registers it (audit)
   * @return the created entry view
   * @throws FinancePeriodClosedException when the target period is CLOSED (BR4)
   */
  @Transactional
  public LedgerEntryView register(
      LedgerDirection direction,
      Party party,
      Money amount,
      EntryType entryType,
      AccountingPeriodId periodId,
      String actor) {
    String period = periodId.value();
    AccountingPeriod accountingPeriod =
        periods.findById(period).orElseGet(() -> periods.save(AccountingPeriod.open(period)));
    if (accountingPeriod.isClosed()) {
      throw new FinancePeriodClosedException();
    }
    Instant now = clock.instant();
    LedgerEntry entry =
        LedgerEntry.register(direction, party, amount, entryType, period, now, actor);
    entries.save(entry);
    events.publishEvent(
        new LedgerEntryRegistered(entry.id(), direction, entryType.name(), period, now));
    log.info(
        "LedgerEntryRegistered entryId={} direction={} type={} period={}",
        entry.id(),
        direction,
        entryType,
        period);
    return entry.toView();
  }

  /**
   * Posts a ledger entry derived from a business fact (SPEC-0015 BR5, DL-0041), idempotently: the
   * fact is identified by {@code (sourceRef, chargeKind)} and posted at most once. The entry is
   * born PROVISIONAL in the period of the fact ({@code occurredAt} in UTC), created lazily OPEN. A
   * re-delivered fact is a no-op (existence pre-check, and the UNIQUE constraint guards a
   * concurrent double-post). A fact whose period is already CLOSED (BR4) is skipped (logged) — a
   * manual adjustment goes to an open period.
   *
   * <p>Runs inside the producer's transaction (the listener is in-process and synchronous), so the
   * entry and its idempotency row are written atomically with the originating fact.
   *
   * @param sourceRef the source fact reference (e.g. the booking id, as text)
   * @param chargeKind the charge kind that produced this entry (value, idempotency key part)
   * @param direction PAYABLE or RECEIVABLE
   * @param party the counterparty
   * @param amount the amount in its original currency (DL-0013)
   * @param entryType the business type
   * @param occurredAt when the fact happened (UTC) — determines the period
   */
  @Transactional
  public void postFromCharge(
      String sourceRef,
      String chargeKind,
      LedgerDirection direction,
      Party party,
      Money amount,
      EntryType entryType,
      Instant occurredAt) {
    if (postedEvents.existsBySourceRefAndChargeKind(sourceRef, chargeKind)) {
      return; // already posted — idempotent no-op (DL-0041)
    }
    String period = YearMonth.from(occurredAt.atZone(ZoneOffset.UTC)).toString();
    AccountingPeriod accountingPeriod =
        periods.findById(period).orElseGet(() -> periods.save(AccountingPeriod.open(period)));
    if (accountingPeriod.isClosed()) {
      log.info(
          "LedgerPostingSkippedClosedPeriod sourceRef={} chargeKind={} period={}",
          sourceRef,
          chargeKind,
          period);
      return; // BR4 — never post into a sealed period; the manual adjustment goes to an open one
    }
    Instant now = clock.instant();
    LedgerEntry entry =
        LedgerEntry.register(direction, party, amount, entryType, period, now, "system");
    entries.save(entry);
    try {
      postedEvents.saveAndFlush(PostedEventEntry.of(sourceRef, chargeKind, entry.id(), now));
    } catch (DataIntegrityViolationException raced) {
      // A concurrent delivery won the race and inserted the idempotency row first; the UNIQUE
      // rejected ours. The transaction rolls back the just-saved entry — net effect: posted once.
      log.info(
          "LedgerPostingRaced sourceRef={} chargeKind={} (already posted concurrently)",
          sourceRef,
          chargeKind);
      throw raced;
    }
    events.publishEvent(
        new LedgerEntryRegistered(entry.id(), direction, entryType.name(), period, now));
    log.info(
        "LedgerEntryRegisteredFromEvent entryId={} sourceRef={} chargeKind={} direction={} type={} period={}",
        entry.id(),
        sourceRef,
        chargeKind,
        direction,
        entryType,
        period);
  }

  /**
   * Confirms a PROVISIONAL entry (BR2).
   *
   * @throws FinanceEntryNotFoundException when the entry does not exist
   * @throws FinanceEntryTransitionInvalidException when not PROVISIONAL (BR2)
   */
  @Transactional
  public LedgerEntryView confirm(UUID entryId, String actor) {
    LedgerEntry entry = entries.findById(entryId).orElseThrow(FinanceEntryNotFoundException::new);
    entry.transitionTo(EntryStatus.CONFIRMED, clock.instant(), actor);
    entries.save(entry);
    log.info("LedgerEntryConfirmed entryId={}", entryId);
    return entry.toView();
  }

  /**
   * Fetches an entry by id.
   *
   * @throws FinanceEntryNotFoundException when the entry does not exist
   */
  @Transactional(readOnly = true)
  public LedgerEntryView getEntry(UUID entryId) {
    return entries
        .findById(entryId)
        .map(LedgerEntry::toView)
        .orElseThrow(FinanceEntryNotFoundException::new);
  }

  /** Lists entries with optional direction, status, period and party filters. */
  @Transactional(readOnly = true)
  public Page<LedgerEntryView> list(
      LedgerDirection direction,
      EntryStatus status,
      String period,
      String party,
      Pageable pageable) {
    return entries
        .search(direction, status, normalize(period), normalize(party), pageable)
        .map(LedgerEntry::toView);
  }

  /**
   * Closes a period (BR3): marks it CLOSING, consults the Compliance veto via {@link CloseGuard},
   * and — only if it may close — seals it CLOSED and publishes {@code PeriodClosed}. If vetoed, the
   * period returns to OPEN and the pending entries are surfaced (no close happens).
   *
   * @param periodId the period to close
   * @param actor who closes it (audit)
   * @return the closed period view
   * @throws FinancePeriodCannotCloseException when the Compliance vetoes the close (BR3)
   */
  @Transactional
  public PeriodView closePeriod(AccountingPeriodId periodId, String actor) {
    String period = periodId.value();
    AccountingPeriod accountingPeriod =
        periods
            .findByIdForUpdate(period)
            .orElseGet(() -> periods.save(AccountingPeriod.open(period)));
    if (accountingPeriod.isClosed()) {
      return toView(accountingPeriod);
    }
    accountingPeriod.beginClosing();
    CloseDecision decision = closeGuard.checkClose(periodId);
    if (!decision.canClose()) {
      accountingPeriod.abortClosing();
      periods.save(accountingPeriod);
      log.info("PeriodCloseBlocked period={} pending={}", period, decision.pending().size());
      throw new FinancePeriodCannotCloseException(decision.pending());
    }
    Instant now = clock.instant();
    accountingPeriod.close(now, actor);
    periods.save(accountingPeriod);
    events.publishEvent(new PeriodClosed(period, now));
    log.info("PeriodClosed period={} closedBy={}", period, actor);
    return toView(accountingPeriod);
  }

  /**
   * Fetches a period view with AP/AR totals per currency (BR-DL-0013); a never-referenced period
   * reads as OPEN with zero totals.
   */
  @Transactional(readOnly = true)
  public PeriodView getPeriod(AccountingPeriodId periodId) {
    String period = periodId.value();
    AccountingPeriod accountingPeriod =
        periods.findById(period).orElseGet(() -> AccountingPeriod.open(period));
    return toView(accountingPeriod);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LedgerEntrySnapshot> entriesOfPeriod(String period) {
    List<LedgerEntrySnapshot> snapshots = new ArrayList<>();
    for (LedgerEntry entry : entries.findByPeriod(period)) {
      snapshots.add(new LedgerEntrySnapshot(entry.id(), entry.entryType().name(), entry.period()));
    }
    return snapshots;
  }

  private PeriodView toView(AccountingPeriod accountingPeriod) {
    List<LedgerEntry> entriesOfPeriod = entries.findByPeriod(accountingPeriod.period());
    return new PeriodView(
        accountingPeriod.period(),
        accountingPeriod.status(),
        totalsByCurrency(entriesOfPeriod, LedgerDirection.PAYABLE),
        totalsByCurrency(entriesOfPeriod, LedgerDirection.RECEIVABLE),
        accountingPeriod.closedAt());
  }

  private static List<Money> totalsByCurrency(
      List<LedgerEntry> entries, LedgerDirection direction) {
    Map<String, BigDecimal> byCurrency = new LinkedHashMap<>();
    for (LedgerEntry entry : entries) {
      if (entry.direction() == direction) {
        Money money = entry.money();
        byCurrency.merge(money.currency(), money.amount(), BigDecimal::add);
      }
    }
    List<Money> totals = new ArrayList<>();
    byCurrency.forEach((currency, amount) -> totals.add(Money.of(amount, currency)));
    return totals;
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
