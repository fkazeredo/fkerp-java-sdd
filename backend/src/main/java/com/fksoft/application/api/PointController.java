package com.fksoft.application.api;

import com.fksoft.domain.people.CrawlRunStatus;
import com.fksoft.domain.people.PointCrawlRunView;
import com.fksoft.domain.people.PointSnapshotService;
import com.fksoft.domain.people.PointSnapshotView;
import com.fksoft.infra.web.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints for the operational side of point integration (SPEC-0012): fetch an operational
 * snapshot and list the crawl-run execution history (BR7). The collection itself is driven by the
 * crawler job/adapter (see {@code PointCrawlController}), not by a public write here.
 */
@RestController
@RequestMapping("/api/integration/point")
@RequiredArgsConstructor
public class PointController {

  /** Hard cap on page size for the run history (avoids unbounded reads). */
  private static final int MAX_PAGE_SIZE = 100;

  private final PointSnapshotService pointSnapshotService;

  @GetMapping("/snapshots/{id}")
  public PointSnapshotView snapshot(@PathVariable UUID id) {
    return pointSnapshotService.getById(id);
  }

  @GetMapping("/runs")
  public PageResponse<PointCrawlRunView> runs(
      @RequestParam(value = "status", required = false) CrawlRunStatus status,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    return PageResponse.from(
        pointSnapshotService.runHistory(status, PageRequest.of(Math.max(page, 0), safeSize)));
  }
}
