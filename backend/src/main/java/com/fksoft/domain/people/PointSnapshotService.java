package com.fksoft.domain.people;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the People module (SPEC-0012, operational side): the owner of the
 * operational point-snapshot collection (idempotent by {@code (sourceRef, periodRef)}, BR5; always
 * {@code operationalOnly = true}, BR3) and of the crawl-run execution history (BR7). It is driven
 * by the infra crawler adapter through this facade only; the external portal shape never reaches
 * here (BR6/DL-0030). It never touches core aggregates of other modules — it only persists its own
 * data and publishes events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointSnapshotService {

  private static final Pattern PERIOD = Pattern.compile("^\\d{4}-\\d{2}$");

  private final PointSnapshotRepository snapshots;
  private final PointCrawlRunRepository runs;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /**
   * Collects an operational snapshot for a source/period, idempotently (BR5): a re-collection of
   * the same {@code (sourceRef, periodRef)} refreshes the captured mirror in place and returns the
   * same snapshot id instead of creating a duplicate. The snapshot is always operational only
   * (BR3). Publishes {@link PointSnapshotCollected}.
   *
   * @param command the translated collect command (the only shape that crosses the ACL boundary)
   * @return the collected (or refreshed) snapshot view
   * @throws PointSnapshotInvalidException when the command is malformed (validation)
   */
  @Transactional
  public PointSnapshotView collect(CollectSnapshotCommand command) {
    validate(command);
    Instant now = clock.instant();
    PointSnapshot snapshot =
        snapshots
            .findBySourceRefAndPeriodRef(command.sourceRef(), command.periodRef())
            .map(
                existing -> {
                  existing.refresh(command.payloadRef(), command.marks(), now);
                  return existing;
                })
            .orElseGet(
                () ->
                    PointSnapshot.collect(
                        command.sourceRef(),
                        command.periodRef(),
                        command.payloadRef(),
                        command.marks(),
                        now));
    try {
      snapshots.save(snapshot);
    } catch (DataIntegrityViolationException race) {
      // Concurrent first-collection of the same (sourceRef, periodRef): fall back to the stored one
      // (BR5). Single-instance (ADR 0002), so this is the rare scheduled+manual overlap.
      PointSnapshot stored =
          snapshots
              .findBySourceRefAndPeriodRef(command.sourceRef(), command.periodRef())
              .orElseThrow(PointSnapshotInvalidException::new);
      log.info(
          "PointSnapshot idempotent hit sourceRef={} periodRef={}",
          command.sourceRef(),
          command.periodRef());
      return stored.toView();
    }
    events.publishEvent(
        new PointSnapshotCollected(snapshot.id(), command.sourceRef(), command.periodRef(), now));
    log.info(
        "PointSnapshotCollected snapshotId={} sourceRef={} periodRef={} marks={} operationalOnly=true",
        snapshot.id(),
        command.sourceRef(),
        command.periodRef(),
        command.marks());
    return snapshot.toView();
  }

  /**
   * Fetches an operational snapshot by id.
   *
   * @throws PointSnapshotNotFoundException when no snapshot has that id
   */
  @Transactional(readOnly = true)
  public PointSnapshotView getById(UUID id) {
    return snapshots
        .findById(id)
        .map(PointSnapshot::toView)
        .orElseThrow(PointSnapshotNotFoundException::new);
  }

  /**
   * Starts a crawl-run history record in {@code RUNNING} (BR7).
   *
   * @param sourceRef the REP/branch reference
   * @param attempts the attempt number (1-based)
   * @param correlationId the correlation id of the run
   * @return the new run id, to be completed by {@link #recordRunSucceeded} or {@link
   *     #recordRunFailed}
   */
  @Transactional
  public UUID startRun(String sourceRef, int attempts, String correlationId) {
    PointCrawlRun run =
        runs.save(PointCrawlRun.start(sourceRef, attempts, correlationId, clock.instant()));
    return run.id();
  }

  /** Marks a crawl run succeeded with the collected period and item count (BR7). */
  @Transactional
  public void recordRunSucceeded(UUID runId, String periodRef, int items) {
    runs.findById(runId).ifPresent(run -> run.succeeded(periodRef, items, clock.instant()));
  }

  /**
   * Marks a crawl run failed (BR7) and publishes {@link PointCrawlingFailed} (BR2) — never a fake
   * snapshot.
   *
   * @param runId the run id
   * @param sourceRef the REP/branch reference
   * @param failureClass the failure classification
   * @param deadLettered whether the run is terminal (attempts exhausted or fatal class)
   */
  @Transactional
  public void recordRunFailed(
      UUID runId, String sourceRef, PointFailureClass failureClass, boolean deadLettered) {
    Instant now = clock.instant();
    runs.findById(runId).ifPresent(run -> run.failed(failureClass, deadLettered, now));
    events.publishEvent(new PointCrawlingFailed(sourceRef, failureClass, deadLettered, now));
    log.info(
        "PointCrawlingFailed sourceRef={} failureClass={} deadLettered={}",
        sourceRef,
        failureClass,
        deadLettered);
  }

  /** Crawl-run history (BR7), optionally filtered by status, newest first. */
  @Transactional(readOnly = true)
  public Page<PointCrawlRunView> runHistory(CrawlRunStatus status, Pageable pageable) {
    Page<PointCrawlRun> page =
        status == null
            ? runs.findAllByOrderByStartedAtDesc(pageable)
            : runs.findByStatusOrderByStartedAtDesc(status, pageable);
    return page.map(PointCrawlRun::toView);
  }

  private static void validate(CollectSnapshotCommand command) {
    if (command == null
        || isBlank(command.sourceRef())
        || isBlank(command.periodRef())
        || !PERIOD.matcher(command.periodRef()).matches()
        || isBlank(command.payloadRef())
        || command.marks() < 0) {
      throw new PointSnapshotInvalidException();
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
