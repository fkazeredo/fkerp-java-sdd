package com.fksoft.domain.reconciliation;

import com.fksoft.domain.exchange.FxPositionService;
import com.fksoft.domain.quoting.QuoteDirectory;
import com.fksoft.domain.quoting.QuoteSnapshot;
import com.fksoft.domain.reconciliation.internal.ReconciliationCase;
import com.fksoft.domain.reconciliation.internal.ReconciliationCaseRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
 * Application service for the reconciliation module (SPEC-0007): opens a case from a confirmed
 * booking (idempotent, BR1), cancels it on booking cancellation (BR2), and records the realized
 * settlement, computing the realized spread, FX gain/loss and discrepancy (BR4-BR7). Reconciliation
 * is read/derivation over facts and never alters other modules (BR8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

  /** Discrepancy tolerance = max(R$1.00, 0.5% of the expected spread) (DL-0011). */
  private static final BigDecimal TOLERANCE_FLOOR = new BigDecimal("1.00");

  private static final BigDecimal TOLERANCE_PCT = new BigDecimal("0.005");

  private final ReconciliationCaseRepository repository;
  private final QuoteDirectory quoteDirectory;
  private final FxPositionService fxPositionService;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Opens a reconciliation case for a confirmed booking, copying the quote's frozen provenance
   * (BR1). Idempotent: a case already exists for the booking is left untouched.
   *
   * @param bookingId the confirmed booking id
   * @param quoteId the originating quote id
   */
  @Transactional
  public void openCase(UUID bookingId, UUID quoteId) {
    if (repository.existsByBookingId(bookingId)) {
      return;
    }
    Optional<QuoteSnapshot> quote = quoteDirectory.find(quoteId);
    if (quote.isEmpty()) {
      log.warn(
          "Cannot open reconciliation case: quote {} missing for booking {}", quoteId, bookingId);
      return;
    }
    Instant now = clock.instant();
    ReconciliationCase reconciliationCase =
        ReconciliationCase.open(bookingId, quote.get(), now, "system");
    try {
      repository.saveAndFlush(reconciliationCase);
    } catch (DataIntegrityViolationException alreadyOpen) {
      log.info("Reconciliation case already open for booking {}", bookingId);
      return;
    }
    events.publishEvent(new ReconciliationCaseOpened(reconciliationCase.id(), bookingId, now));
    log.info("ReconciliationCaseOpened caseId={} bookingId={}", reconciliationCase.id(), bookingId);

    // Open the matching FX position in Exchange from the same frozen provenance (SPEC-0011 BR2,
    // DL-0028). Exchange owns the subsidy/drift math; we pass it the foreign cost and frozen rate
    // (no duplicated per-case computation, no cycle: reconciliation -> exchange).
    QuoteSnapshot snapshot = quote.get();
    fxPositionService.openPosition(bookingId, snapshot.basePrice(), snapshot.pinnedRate(), now);
  }

  /** Cancels the case for a cancelled booking, if one exists and is not already cancelled (BR2). */
  @Transactional
  public void cancelCase(UUID bookingId) {
    repository
        .findByBookingId(bookingId)
        .filter(reconciliationCase -> reconciliationCase.status() != CaseStatus.CANCELLED)
        .ifPresent(
            reconciliationCase -> {
              reconciliationCase.cancel(clock.instant(), "system");
              repository.save(reconciliationCase);
              log.info("ReconciliationCaseCancelled bookingId={}", bookingId);
            });
  }

  /**
   * Records realized settlement values and recomputes the derivations (BR3-BR7), under a
   * pessimistic lock. Publishes {@code SpreadRealized} when the realized spread is computed and
   * {@code ReconciliationDiscrepancyFlagged} when it exceeds tolerance.
   *
   * @param caseId the case id
   * @param input the realized values
   * @param actor who records it (audit)
   * @return the updated case view
   * @throws ReconciliationCaseNotFoundException when the case does not exist
   * @throws ReconciliationCurrencyMismatchException when a value is in the wrong currency
   */
  @Transactional
  public ReconciliationCaseView recordSettlement(UUID caseId, SettlementInput input, String actor) {
    ReconciliationCase reconciliationCase =
        repository.findByIdForUpdate(caseId).orElseThrow(ReconciliationCaseNotFoundException::new);
    Instant now = clock.instant();
    reconciliationCase.settle(input, TOLERANCE_FLOOR, TOLERANCE_PCT, now, actor);
    repository.save(reconciliationCase);

    ReconciliationCaseView view = reconciliationCase.toView();
    if (view.realizedSpread() != null) {
      events.publishEvent(
          new SpreadRealized(caseId, view.realizedSpread(), view.fxGainLoss(), now));
    }
    if (reconciliationCase.isDiscrepancy()) {
      events.publishEvent(
          new ReconciliationDiscrepancyFlagged(
              caseId, view.expectedSpread(), view.realizedSpread(), view.discrepancy(), now));
    }

    // Close the matching FX position with the settlement rate just recorded (SPEC-0011 BR5,
    // DL-0028). Reusing this rate avoids duplicating the per-case FX math in Exchange; a no-op when
    // there is no FX position (e.g. a BRL-cost sale) or the rate is not yet recorded.
    BigDecimal settlementRate = reconciliationCase.supplierSettlementRate();
    if (settlementRate != null) {
      fxPositionService.closePosition(reconciliationCase.bookingId(), settlementRate, now);
    }

    log.info("ReconciliationSettlement caseId={} status={}", caseId, view.status());
    return view;
  }

  /**
   * Fetches a case by id.
   *
   * @throws ReconciliationCaseNotFoundException when the case does not exist
   */
  @Transactional(readOnly = true)
  public ReconciliationCaseView getById(UUID caseId) {
    return repository
        .findById(caseId)
        .map(ReconciliationCase::toView)
        .orElseThrow(ReconciliationCaseNotFoundException::new);
  }

  /** Lists cases ordered by discrepancy desc, with optional status and min-discrepancy filters. */
  @Transactional(readOnly = true)
  public Page<ReconciliationCaseView> list(
      CaseStatus status, BigDecimal minDiscrepancy, Pageable pageable) {
    return repository.search(status, minDiscrepancy, pageable).map(ReconciliationCase::toView);
  }
}
