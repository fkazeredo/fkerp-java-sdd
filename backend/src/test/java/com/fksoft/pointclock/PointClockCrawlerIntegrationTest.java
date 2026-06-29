package com.fksoft.pointclock;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.people.CrawlRunStatus;
import com.fksoft.domain.people.PointFailureClass;
import com.fksoft.domain.people.PointSnapshotService;
import com.fksoft.domain.people.PointSnapshotView;
import com.fksoft.infra.integration.pointclock.FaultInjectingPointClockSource;
import com.fksoft.infra.integration.pointclock.PointClockCircuitBreaker;
import com.fksoft.infra.integration.pointclock.PointClockCrawler;
import com.fksoft.infra.integration.pointclock.PointClockSource;
import com.fksoft.infra.integration.pointclock.PointMirrorTranslator;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end resilience tests for the point-clock crawler (SPEC-0012 slice 11b) against real
 * Postgres with a <strong>fault-injecting</strong> portal source (DL-0031, {@code
 * simulation-and-mocking.md}). Each test builds its own {@link PointClockCrawler} wired to a
 * scripted source (failureThreshold=2, a large cooldown so OPEN stays open, maxAttempts=3) so the
 * breaker, retry and dead-letter are deterministic. Covers: a successful crawl publishes an
 * (idempotent) snapshot and records a SUCCEEDED run; a retryable failure that recovers within max
 * attempts succeeds; a persistent retryable failure exhausts attempts → DEAD_LETTER + {@code
 * PointCrawlingFailed} and no snapshot; a fatal AUTHENTICATION_FAILED is not retried (single hit);
 * the breaker opens after repeated failures and short-circuits the next call WITHOUT hitting the
 * portal.
 */
class PointClockCrawlerIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private PointSnapshotService pointSnapshotService;
  @Autowired private PointMirrorTranslator translator;
  @Autowired private Clock clock;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM point_snapshots");
    jdbcTemplate.execute("DELETE FROM point_crawl_runs");
  }

  private PointClockCrawler crawlerWith(PointClockSource source) {
    // failureThreshold=2, cooldown large (so OPEN stays open in-test), maxAttempts=3.
    return new PointClockCrawler(source, translator, pointSnapshotService, clock, 2, 600_000L, 3);
  }

  @Test
  void successfulCrawlPublishesAnOperationalSnapshotAndRecordsTheRun() {
    FaultInjectingPointClockSource source = new FaultInjectingPointClockSource().thenSucceed(482);
    PointClockCrawler crawler = crawlerWith(source);

    PointSnapshotView snapshot = crawler.crawl("REP-FILIAL-SP");

    assertThat(snapshot).isNotNull();
    assertThat(snapshot.operationalOnly()).isTrue();
    assertThat(snapshot.marks()).isEqualTo(482);
    assertThat(runCount(CrawlRunStatus.SUCCEEDED)).isEqualTo(1);
    assertThat(snapshotCount()).isEqualTo(1);
  }

  @Test
  void retryableFailureThatRecoversWithinAttemptsSucceeds() {
    FaultInjectingPointClockSource source =
        new FaultInjectingPointClockSource().thenFail(PointFailureClass.TIMEOUT).thenSucceed(100);
    PointClockCrawler crawler = crawlerWith(source);

    PointSnapshotView snapshot = crawler.crawl("REP-FILIAL-SP");

    assertThat(snapshot).isNotNull();
    assertThat(snapshot.marks()).isEqualTo(100);
    assertThat(source.calls()).isEqualTo(2); // one failed attempt + one success
    assertThat(runCount(CrawlRunStatus.SUCCEEDED)).isEqualTo(1);
    assertThat(runCount(CrawlRunStatus.RETRY_SCHEDULED)).isEqualTo(1);
  }

  @Test
  void persistentRetryableFailureExhaustsAttemptsAndDeadLettersWithoutSnapshot() {
    FaultInjectingPointClockSource source =
        new FaultInjectingPointClockSource()
            .thenFail(PointFailureClass.UNAVAILABLE)
            .thenFail(PointFailureClass.UNAVAILABLE)
            .thenFail(PointFailureClass.UNAVAILABLE);
    PointClockCrawler crawler = crawlerWith(source);

    PointSnapshotView snapshot = crawler.crawl("REP-FILIAL-SP");

    assertThat(snapshot).isNull(); // never a fake snapshot
    assertThat(snapshotCount()).isZero();
    assertThat(runCount(CrawlRunStatus.DEAD_LETTER)).isEqualTo(1);
    Integer deadLetterUnavailable =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM point_crawl_runs WHERE status = ? AND failure_class = ?",
            Integer.class,
            CrawlRunStatus.DEAD_LETTER.name(),
            PointFailureClass.UNAVAILABLE.name());
    assertThat(deadLetterUnavailable).isEqualTo(1);
  }

  @Test
  void authenticationFailureIsFatalAndNotRetried() {
    FaultInjectingPointClockSource source =
        new FaultInjectingPointClockSource()
            .thenFail(PointFailureClass.AUTHENTICATION_FAILED)
            .thenSucceed(999); // would succeed if it retried — it must not
    PointClockCrawler crawler = crawlerWith(source);

    PointSnapshotView snapshot = crawler.crawl("REP-FILIAL-SP");

    assertThat(snapshot).isNull();
    assertThat(source.calls()).isEqualTo(1); // fatal class → no retry
    assertThat(runCount(CrawlRunStatus.DEAD_LETTER)).isEqualTo(1);
    assertThat(snapshotCount()).isZero();
  }

  @Test
  void breakerOpensAfterRepeatedFailuresAndShortCircuitsTheNextCallWithoutHittingThePortal() {
    // Threshold 2: two failures in a single crawl open the breaker. Re-using the SAME crawler keeps
    // the breaker state; the next crawl is short-circuited without hitting the portal.
    FaultInjectingPointClockSource source =
        new FaultInjectingPointClockSource()
            .thenFail(PointFailureClass.UNAVAILABLE)
            .thenFail(PointFailureClass.UNAVAILABLE)
            .thenSucceed(1); // would be reached only if the breaker were closed
    PointClockCrawler crawler = crawlerWith(source);

    PointSnapshotView first =
        crawler.crawl("REP-FILIAL-SP"); // exhausts attempts? no: maxAttempts=3
    // With threshold 2, the 2nd failure opens the breaker; the 3rd attempt is short-circuited.
    assertThat(first).isNull();
    assertThat(crawler.breakerState()).isEqualTo(PointClockCircuitBreaker.State.OPEN);
    int callsAfterFirst = source.calls();

    PointSnapshotView second = crawler.crawl("REP-FILIAL-SP"); // breaker open → short-circuit

    assertThat(second).isNull();
    assertThat(source.calls())
        .as("a short-circuited call must NOT hit the portal")
        .isEqualTo(callsAfterFirst);
    assertThat(snapshotCount()).isZero();
  }

  private Integer runCount(CrawlRunStatus status) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM point_crawl_runs WHERE status = ?", Integer.class, status.name());
  }

  private Integer snapshotCount() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM point_snapshots", Integer.class);
  }
}
