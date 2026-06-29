package com.fksoft.infra.jobs;

import com.fksoft.infra.integration.pointclock.PointClockCrawler;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically runs the point-clock crawl for each configured REP/
 * branch (SPEC-0012 BR9/DL-0033; redesign: the Platform orchestrates the crawler). The resilience,
 * idempotency and history live in {@link PointClockCrawler} / the People module; this adapter only
 * supplies the schedule and the source list. The crawl is idempotent by {@code (sourceRef,
 * periodRef)} (BR5), so overlapping scheduled/manual runs are safe.
 */
@Slf4j
@Component
public class PointClockCrawlScheduler {

  private final PointClockCrawler crawler;
  private final List<String> sources;

  public PointClockCrawlScheduler(
      PointClockCrawler crawler,
      @Value("${point-clock.sources:REP-DEFAULT}") List<String> sources) {
    this.crawler = crawler;
    this.sources = sources;
  }

  /**
   * Runs the crawl for every configured source. Interval/initial-delay configurable (default
   * daily).
   */
  @Scheduled(
      initialDelayString = "${point-clock.crawl.initial-delay-ms:600000}",
      fixedDelayString = "${point-clock.crawl.interval-ms:86400000}")
  public void crawlAllSources() {
    for (String sourceRef : sources) {
      try {
        crawler.crawl(sourceRef);
      } catch (RuntimeException unexpected) {
        // The crawler classifies and dead-letters its own failures; this guard only keeps one
        // source's unexpected error from skipping the others (partial failure, safe restart).
        log.error("PointClock scheduled crawl errored for sourceRef={}", sourceRef, unexpected);
      }
    }
  }
}
