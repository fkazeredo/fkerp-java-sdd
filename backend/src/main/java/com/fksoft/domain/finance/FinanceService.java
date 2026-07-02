package com.fksoft.domain.finance;

import com.fksoft.domain.cadastro.CadastroType;
import com.fksoft.domain.cadastro.CadastroValidator;
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
  private final CadastroValidator cadastroValidator;

  /**
   * Registers a ledger entry in PROVISIONAL (BR2) against an open period (creating the period
   * lazily OPEN, BR4). Publishes {@code LedgerEntryRegistered}.
   *
   * @param direction PAYABLE or RECEIVABLE
   * @param party the counterparty (party-type cadastro code validated)
   * @param amount the amount in its original currency (DL-0013)
   * @param entryType the business type (entry-type cadastro code — the Compliance key; validated)
   * @param periodId the target period
   * @param actor who registers it (audit)
   * @return the created entry view
   * @throws FinancePeriodClosedException when the target period is CLOSED (BR4)
   * @throws com.fksoft.domain.cadastro.CadastroCodeInvalidException when a code is unknown/inactive
   */
  @Transactional
  public LedgerEntryView register(
      LedgerDirection direction,
      Party party,
      Money amount,
      String entryType,
      AccountingPeriodId periodId,
      String actor) {
    // Validate the reference codes against the cadastro (SPEC-0031 BR3/DL-0118). Internal producers
    // pass wired *Codes constants (always valid); a manual REST create is rejected (422) on an
    // unknown/inactive code.
    cadastroValidator.validate(CadastroType.ENTRY_TYPE, entryType);
    cadastroValidator.validate(CadastroType.PARTY_TYPE, party.type());
    String period = periodId.value();
    // Lock the period row (same lock closePeriod takes) so a register racing a close serializes:
    // either the entry lands before the seal, or it re-reads CLOSED and is rejected (BR4). Without
    // the lock an entry could slip into a period sealed between the read and the insert
    // (SPEC-0015 BR4-bis, Fase 19i/DL-0131).
    AccountingPeriod accountingPeriod =
        periods
            .findByIdForUpdate(period)
            .orElseGet(() -> periods.save(AccountingPeriod.open(period)));
    if (accountingPeriod.isClosed()) {
      throw new FinancePeriodClosedException();
    }
    Instant now = clock.instant();
    LedgerEntry entry =
        LedgerEntry.register(direction, party, amount, entryType, period, now, actor);
    entries.save(entry);
    events.publishEvent(new LedgerEntryRegistered(entry.id(), direction, entryType, period, now));
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
   * @param entryType the business type (entry-type cadastro code)
   * @param occurredAt when the fact happened (UTC) — determines the period
   */
  @Transactional
  public void postFromCharge(
      String sourceRef,
      String chargeKind,
      LedgerDirection direction,
      Party party,
      Money amount,
      String entryType,
      Instant occurredAt) {
    if (postedEvents.existsBySourceRefAndChargeKind(sourceRef, chargeKind)) {
      return; // already posted — idempotent no-op (DL-0041)
    }
    String period = YearMonth.from(occurredAt.atZone(ZoneOffset.UTC)).toString();
    // Same period lock as register/closePeriod (BR4-bis, Fase 19i/DL-0131): a posting racing the
    // monthly close serializes instead of slipping an entry into the just-sealed period.
    AccountingPeriod accountingPeriod =
        periods
            .findByIdForUpdate(period)
            .orElseGet(() -> periods.save(AccountingPeriod.open(period)));
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
    events.publishEvent(new LedgerEntryRegistered(entry.id(), direction, entryType, period, now));
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

  /**
   * Builds the operational trial-balance of a period (SPEC-0015 BR10, DL-0043): AP/AR totals and
   * net per currency (DL-0013 — never summing currencies) and the entry counts per status. A
   * never-referenced period reads as an empty balance with zero counts.
   *
   * @param periodId the period
   * @return the trial-balance view
   */
  @Transactional(readOnly = true)
  public TrialBalanceView trialBalance(AccountingPeriodId periodId) {
    String period = periodId.value();
    AccountingPeriod accountingPeriod =
        periods.findById(period).orElseGet(() -> AccountingPeriod.open(period));
    List<LedgerEntry> entriesOfPeriod = entries.findByPeriod(period);

    Map<String, BigDecimal> payableByCurrency = new LinkedHashMap<>();
    Map<String, BigDecimal> receivableByCurrency = new LinkedHashMap<>();
    long provisional = 0;
    long confirmed = 0;
    long settled = 0;
    for (LedgerEntry entry : entriesOfPeriod) {
      Money money = entry.money();
      Map<String, BigDecimal> target =
          entry.direction() == LedgerDirection.PAYABLE ? payableByCurrency : receivableByCurrency;
      target.merge(money.currency(), money.amount(), BigDecimal::add);
      // Make sure the other side has an entry for this currency too, so net is computed once.
      (entry.direction() == LedgerDirection.PAYABLE ? receivableByCurrency : payableByCurrency)
          .putIfAbsent(money.currency(), BigDecimal.ZERO);
      switch (entry.status()) {
        case PROVISIONAL -> provisional++;
        case CONFIRMED -> confirmed++;
        case SETTLED -> settled++;
      }
    }

    List<TrialBalanceView.CurrencyBalance> balances = new ArrayList<>();
    for (String currency : payableByCurrency.keySet()) {
      BigDecimal payable = scaled(payableByCurrency.get(currency));
      BigDecimal receivable = scaled(receivableByCurrency.getOrDefault(currency, BigDecimal.ZERO));
      balances.add(
          new TrialBalanceView.CurrencyBalance(
              currency, payable, receivable, receivable.subtract(payable)));
    }

    return new TrialBalanceView(
        period, accountingPeriod.status(), balances, provisional, confirmed, settled);
  }

  private static BigDecimal scaled(BigDecimal amount) {
    return amount.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LedgerEntrySnapshot> entriesOfPeriod(String period) {
    List<LedgerEntrySnapshot> snapshots = new ArrayList<>();
    for (LedgerEntry entry : entries.findByPeriod(period)) {
      snapshots.add(new LedgerEntrySnapshot(entry.id(), entry.entryType(), entry.period()));
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
