package com.fksoft.domain.people.internal;

import com.fksoft.domain.people.CrawlRunStatus;
import com.fksoft.domain.people.PointCrawlRunView;
import com.fksoft.domain.people.PointFailureClass;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Execution-history record of one crawl run (SPEC-0012 BR7; {@code messaging-and-integrations.md} —
 * important jobs keep history). It captures start/finish, the status lifecycle (RUNNING →
 * SUCCEEDED/RETRY_SCHEDULED/DEAD_LETTER), the attempt count, item/failure counts, the failure class
 * and the correlation id. Module-internal.
 */
@Entity
@Table(name = "point_crawl_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointCrawlRun {

  @Id private UUID id;

  private String sourceRef;

  private String periodRef;

  private Instant startedAt;

  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  private CrawlRunStatus status;

  private int attempts;

  private Integer items;

  private Integer failures;

  @Enumerated(EnumType.STRING)
  private PointFailureClass failureClass;

  private String correlationId;

  /**
   * Starts a run record in {@code RUNNING} (BR7).
   *
   * @param sourceRef the REP/branch reference
   * @param attempts the attempt number this run represents (1-based)
   * @param correlationId the correlation id of the run
   * @param now the start instant (UTC)
   * @return a new, persistable run record
   */
  public static PointCrawlRun start(
      String sourceRef, int attempts, String correlationId, Instant now) {
    PointCrawlRun run = new PointCrawlRun();
    run.id = UUID.randomUUID();
    run.sourceRef = sourceRef;
    run.attempts = attempts;
    run.correlationId = correlationId;
    run.startedAt = now;
    run.status = CrawlRunStatus.RUNNING;
    return run;
  }

  /**
   * Marks the run succeeded with the resolved period and the number of collected items (BR7).
   *
   * @param periodRef the collected period ({@code YYYY-MM})
   * @param items the number of punches collected
   * @param now the finish instant (UTC)
   */
  public void succeeded(String periodRef, int items, Instant now) {
    this.periodRef = periodRef;
    this.items = items;
    this.failures = 0;
    this.status = CrawlRunStatus.SUCCEEDED;
    this.finishedAt = now;
  }

  /**
   * Marks the run failed (BR7), either scheduled for retry or dead-lettered, with the failure
   * class.
   *
   * @param failureClass the failure classification
   * @param deadLettered {@code true} for the terminal dead-letter state; {@code false} for retry
   * @param now the finish instant (UTC)
   */
  public void failed(PointFailureClass failureClass, boolean deadLettered, Instant now) {
    this.failureClass = failureClass;
    this.failures = 1;
    this.status = deadLettered ? CrawlRunStatus.DEAD_LETTER : CrawlRunStatus.RETRY_SCHEDULED;
    this.finishedAt = now;
  }

  /** The run id. */
  public UUID id() {
    return id;
  }

  /** Projects the run to its public read view. */
  public PointCrawlRunView toView() {
    return new PointCrawlRunView(
        id,
        sourceRef,
        periodRef,
        status,
        attempts,
        items,
        failures,
        failureClass,
        startedAt,
        finishedAt,
        correlationId);
  }
}
