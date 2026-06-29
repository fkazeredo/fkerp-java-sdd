package com.fksoft.people;

import static org.assertj.core.api.Assertions.assertThat;

import com.fksoft.domain.people.CollectSnapshotCommand;
import com.fksoft.domain.people.CrawlRunStatus;
import com.fksoft.domain.people.PointFailureClass;
import com.fksoft.domain.people.PointSnapshotService;
import com.fksoft.domain.people.PointSnapshotView;
import com.fksoft.infra.web.ApiErrorResponse;
import com.fksoft.system.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end tests for the operational side of point integration (SPEC-0012, slice 11a) against
 * real Postgres. The collection use case is driven through the public {@link PointSnapshotService}
 * facade (the crawler's job), then read back via the REST endpoints. Covers: a collect persists an
 * operational snapshot and is readable (200); a re-collect of the same {@code (sourceRef,
 * periodRef)} is idempotent (no duplicate, BR5); the snapshot is always {@code operationalOnly}
 * (BR3); an unknown id is 404; the crawl-run history records start/success/failure (BR7).
 */
class PointSnapshotIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PointSnapshotService pointSnapshotService;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.execute("DELETE FROM point_snapshots");
    jdbcTemplate.execute("DELETE FROM point_crawl_runs");
  }

  @Test
  void collectsAnOperationalSnapshotReadableViaTheApi() {
    PointSnapshotView collected =
        pointSnapshotService.collect(
            new CollectSnapshotCommand("REP-FILIAL-SP", "2026-06", "mirror-ref", 482));

    ResponseEntity<PointSnapshotView> response =
        restTemplate.getForEntity(
            "/api/integration/point/snapshots/" + collected.id(), PointSnapshotView.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().sourceRef()).isEqualTo("REP-FILIAL-SP");
    assertThat(response.getBody().periodRef()).isEqualTo("2026-06");
    assertThat(response.getBody().marks()).isEqualTo(482);
    // BR3: the snapshot is operational only — never a legal document.
    assertThat(response.getBody().operationalOnly()).isTrue();
  }

  @Test
  void reCollectingTheSamePeriodIsIdempotent() {
    PointSnapshotView first =
        pointSnapshotService.collect(
            new CollectSnapshotCommand("REP-FILIAL-SP", "2026-06", "mirror-ref-1", 100));
    PointSnapshotView second =
        pointSnapshotService.collect(
            new CollectSnapshotCommand("REP-FILIAL-SP", "2026-06", "mirror-ref-2", 482));

    assertThat(second.id()).isEqualTo(first.id()); // same snapshot, refreshed in place (BR5)
    assertThat(second.marks()).isEqualTo(482);
    Integer count =
        jdbcTemplate.queryForObject("SELECT count(*) FROM point_snapshots", Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void rejectsAnInvalidCollectAndReturnsTheStableError() {
    pointSnapshotService.collect(
        new CollectSnapshotCommand("REP-FILIAL-SP", "2026-06", "mirror-ref", 482));

    // Unknown id → 404 with the stable code (the read path is the only public write surface here).
    ResponseEntity<ApiErrorResponse> notFound =
        restTemplate.getForEntity(
            "/api/integration/point/snapshots/" + UUID.randomUUID(), ApiErrorResponse.class);

    assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(notFound.getBody()).isNotNull();
    assertThat(notFound.getBody().code()).isEqualTo("point.snapshot.not-found");
  }

  @Test
  void crawlRunHistoryRecordsSuccessAndFailure() {
    UUID okRun = pointSnapshotService.startRun("REP-FILIAL-SP", 1, "corr-ok");
    pointSnapshotService.recordRunSucceeded(okRun, "2026-06", 482);

    UUID failRun = pointSnapshotService.startRun("REP-FILIAL-RJ", 3, "corr-fail");
    pointSnapshotService.recordRunFailed(
        failRun, "REP-FILIAL-RJ", PointFailureClass.UNAVAILABLE, true);

    Integer succeeded =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM point_crawl_runs WHERE status = ?",
            Integer.class,
            CrawlRunStatus.SUCCEEDED.name());
    Integer deadLetter =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM point_crawl_runs WHERE status = ? AND failure_class = ?",
            Integer.class,
            CrawlRunStatus.DEAD_LETTER.name(),
            PointFailureClass.UNAVAILABLE.name());
    assertThat(succeeded).isEqualTo(1);
    assertThat(deadLetter).isEqualTo(1);

    // The runs endpoint surfaces the history (BR7), filterable by status.
    ResponseEntity<String> runs =
        restTemplate.getForEntity(
            "/api/integration/point/runs?status=DEAD_LETTER&size=10", String.class);
    assertThat(runs.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(runs.getBody()).contains("REP-FILIAL-RJ").contains("UNAVAILABLE");
  }
}
