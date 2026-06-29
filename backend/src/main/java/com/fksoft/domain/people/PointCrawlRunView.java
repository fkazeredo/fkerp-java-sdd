package com.fksoft.domain.people;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a crawl-run history record (SPEC-0012 BR7). Surfaces start/finish, status,
 * attempts, item/failure counts, the failure class (when failed) and the correlation id — the
 * audit/observability trail of one execution of the crawl job.
 *
 * @param id the run id
 * @param sourceRef the REP/branch reference
 * @param periodRef the resolved period, or {@code null} if it failed before resolving it
 * @param status the run status
 * @param attempts how many attempts the run accumulated
 * @param items collected punches on success, or {@code null}
 * @param failures number of failures, or {@code null}
 * @param failureClass the failure classification when the run failed, or {@code null}
 * @param startedAt when the run started
 * @param finishedAt when the run finished, or {@code null} if still running
 * @param correlationId the correlation id of the run
 */
public record PointCrawlRunView(
    UUID id,
    String sourceRef,
    String periodRef,
    CrawlRunStatus status,
    int attempts,
    Integer items,
    Integer failures,
    PointFailureClass failureClass,
    Instant startedAt,
    Instant finishedAt,
    String correlationId) {}
