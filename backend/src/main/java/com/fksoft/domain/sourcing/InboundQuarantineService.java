package com.fksoft.domain.sourcing;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service of the inbound exception queue (SPEC-0009 BR10, DL-0120 — revises DL-0017). A
 * rejected inbound payload is kept in quarantine so the operator can fix the cause and
 * <strong>replay</strong> it (or discard it). Recording runs in its own transaction ({@code
 * REQUIRES_NEW}) because it happens on the failure path of the inbound processing — the quarantine
 * row must survive the 422 the caller still receives (wire contract unchanged).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundQuarantineService {

  private final InboundQuarantineRepository repository;
  private final SourcingService sourcingService;
  private final Clock clock;

  /**
   * Quarantines a rejected inbound command. Idempotent per {@code externalQuotationId}: a
   * re-delivery of the same rejected payload keeps the single pending entry.
   *
   * @param command the translated inbound command that was rejected
   * @param reasonCode the stable rejection reason (i18n key)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void quarantine(RegisterInboundQuotationCommand command, String reasonCode) {
    if (repository.existsByExternalQuotationIdAndStatus(
        command.externalQuotationId(), InboundQuarantineStatus.QUARANTINED)) {
      log.info(
          "InboundQuarantine duplicate externalQuotationId={} (pending entry kept)",
          command.externalQuotationId());
      return;
    }
    InboundQuarantineEntry entry =
        repository.save(InboundQuarantineEntry.quarantine(command, reasonCode, clock.instant()));
    log.info(
        "InboundQuarantined id={} externalQuotationId={} reason={}",
        entry.id(),
        entry.externalQuotationId(),
        reasonCode);
  }

  /**
   * Replays a pending entry through the normal inbound processing. On success the entry is marked
   * {@code REPLAYED} and linked to the created quote. If the original cause persists (e.g. the
   * account is still unknown), the same domain exception propagates and the entry stays pending.
   *
   * @param id the quarantine entry id
   * @param actor the operator performing the replay (audit)
   * @return the updated entry view (status {@code REPLAYED}, with the quote id)
   * @throws InboundQuarantineNotFoundException when no entry has that id
   * @throws InboundQuarantineNotPendingException when the entry was already replayed/discarded
   */
  @Transactional
  public InboundQuarantineView replay(UUID id, String actor) {
    InboundQuarantineEntry entry =
        repository.findById(id).orElseThrow(InboundQuarantineNotFoundException::new);
    if (entry.status() != InboundQuarantineStatus.QUARANTINED) {
      throw new InboundQuarantineNotPendingException();
    }
    InboundQuotationResult result = sourcingService.processInbound(entry.toCommand(), actor);
    entry.markReplayed(result.quoteId(), clock.instant(), actor);
    repository.save(entry);
    log.info("InboundQuarantineReplayed id={} quoteId={} actor={}", id, result.quoteId(), actor);
    return entry.toView();
  }

  /**
   * Discards a pending entry (the operator decided the payload should not enter the system).
   *
   * @throws InboundQuarantineNotFoundException when no entry has that id
   * @throws InboundQuarantineNotPendingException when the entry was already replayed/discarded
   */
  @Transactional
  public InboundQuarantineView discard(UUID id, String actor) {
    InboundQuarantineEntry entry =
        repository.findById(id).orElseThrow(InboundQuarantineNotFoundException::new);
    entry.discard(clock.instant(), actor);
    repository.save(entry);
    log.info("InboundQuarantineDiscarded id={} actor={}", id, actor);
    return entry.toView();
  }

  /** Lists quarantine entries, optionally filtered by status, newest first. */
  @Transactional(readOnly = true)
  public List<InboundQuarantineView> list(InboundQuarantineStatus status) {
    List<InboundQuarantineEntry> entries =
        status == null
            ? repository.findAllByOrderByReceivedAtDesc()
            : repository.findByStatusOrderByReceivedAtDesc(status);
    return entries.stream().map(InboundQuarantineEntry::toView).toList();
  }
}
