package com.fksoft.domain.exchange;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for FX forward contracts (SPEC-0032, Fase 19h/DL-0130): manual registration,
 * settlement at the effective rate and cancellation. Forwards are the treasury lever that hedges
 * the exposure the book accumulates; the OPEN ones reduce the unhedged exposure the drift alert
 * watches ({@link ExchangeExposureService}). No bank integration — the fact is registered by the
 * treasury desk (Director/Finance, Fase 19a matrix).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForwardService {

  private final ForwardContractRepository repository;
  private final Clock clock;

  /**
   * Registers a forward (OPEN).
   *
   * @throws ForwardContractInvalidException when an invariant is violated (400)
   */
  @Transactional
  public ForwardContractView register(
      String currency,
      BigDecimal notional,
      BigDecimal contractRate,
      LocalDate tradeDate,
      LocalDate maturityDate,
      String counterparty,
      String actor) {
    ForwardContract forward =
        repository.save(
            ForwardContract.register(
                currency,
                notional,
                contractRate,
                tradeDate,
                maturityDate,
                counterparty,
                clock.instant(),
                actor));
    log.info(
        "FxForwardRegistered id={} currency={} notional={} rate={} maturity={}",
        forward.id(),
        forward.currency(),
        forward.notional(),
        forward.contractRate(),
        forward.maturityDate());
    return forward.toView();
  }

  /**
   * Settles a forward at the effective rate (OPEN → SETTLED).
   *
   * @throws ForwardContractNotFoundException when the id is unknown (404)
   * @throws ForwardContractNotOpenException when not OPEN (409)
   */
  @Transactional
  public ForwardContractView settle(UUID id, BigDecimal effectiveRate, String actor) {
    ForwardContract forward =
        repository.findById(id).orElseThrow(ForwardContractNotFoundException::new);
    forward.settle(effectiveRate, clock.instant(), actor);
    repository.save(forward);
    log.info(
        "FxForwardSettled id={} settledRate={} resultBrl={}",
        id,
        effectiveRate,
        forward.settlementResultBrl());
    return forward.toView();
  }

  /**
   * Cancels a forward (OPEN → CANCELLED).
   *
   * @throws ForwardContractNotFoundException when the id is unknown (404)
   * @throws ForwardContractNotOpenException when not OPEN (409)
   */
  @Transactional
  public ForwardContractView cancel(UUID id, String actor) {
    ForwardContract forward =
        repository.findById(id).orElseThrow(ForwardContractNotFoundException::new);
    forward.cancel(clock.instant(), actor);
    repository.save(forward);
    log.info("FxForwardCancelled id={}", id);
    return forward.toView();
  }

  /** Lists forwards, optionally by status, newest first (all) / nearest maturity first (status). */
  @Transactional(readOnly = true)
  public List<ForwardContractView> list(ForwardStatus status) {
    List<ForwardContract> forwards =
        status == null
            ? repository.findAllByOrderByCreatedAtDesc()
            : repository.findByStatusOrderByMaturityDateAsc(status);
    return forwards.stream().map(ForwardContract::toView).toList();
  }
}
