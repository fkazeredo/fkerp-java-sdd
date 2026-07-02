package com.fksoft.application.api;

import com.fksoft.domain.people.PointSnapshotView;
import com.fksoft.infra.integration.pointclock.PointClockCrawler;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for the point-clock crawl (SPEC-0012 API Contracts), beyond the scheduled job.
 * Intended for operational/IT roles. Delegates to the {@link PointClockCrawler} (which owns the
 * circuit breaker, retry, dead-letter and history). Returns {@code 202 Accepted} with the collected
 * snapshot when the crawl succeeded, or {@code 202} with an empty body when it could not produce a
 * snapshot (failure was classified, dead-lettered and a {@code PointCrawlingFailed} event published
 * — never a fake snapshot).
 */
@Tag(name = "Point Crawl", description = "Disparo da coleta de ponto")
@RestController
@RequestMapping("/api/integration/point")
@RequiredArgsConstructor
public class PointCrawlController {

  private final PointClockCrawler crawler;

  @PostMapping("/crawl")
  public ResponseEntity<PointSnapshotView> crawl(
      @RequestParam(value = "sourceRef", defaultValue = "REP-DEFAULT") String sourceRef) {
    PointSnapshotView snapshot = crawler.crawl(sourceRef);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(snapshot);
  }
}
