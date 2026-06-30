package com.fksoft.infra.jobs;

import com.fksoft.domain.platform.JobOutcome;
import com.fksoft.infra.integration.pointclock.PointClockCrawler;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Technical driving adapter that periodically runs the point-clock crawl for each configured REP/
 * branch (SPEC-0012 BR9/DL-0033), <strong>through the Platform job governance</strong> (SPEC-0023
 * BR2/BR3; DL-0076). The crawler ({@link PointClockCrawler}) keeps its own outbound resilience
 * (circuit breaker, retry, dead-letter) and the snapshot idempotency by {@code (sourceRef,
 * periodRef)} in People; running through {@link GovernedJobs} adds the lock, a per-month+source
 * idempotency window and the {@code JobRun} history. A crawl that dead-letters surfaces as a FAILED
 * run (BR3); the per-source guard keeps one source's failure from skipping the others.
 */
@Slf4j
@Component
public class PointClockCrawlScheduler {

  private final PointClockCrawler crawler;
  private final GovernedJobs governedJobs;
  private final List<String> sources;

  public PointClockCrawlScheduler(
      PointClockCrawler crawler,
      GovernedJobs governedJobs,
      @Value("${point-clock.sources:REP-DEFAULT}") List<String> sources) {
    this.crawler = crawler;
    this.governedJobs = governedJobs;
    this.sources = sources;
  }

  /**
   * Runs the crawl for every configured source under governance. Interval/initial-delay
   * configurable (default daily).
   */
  @Scheduled(
      initialDelayString = "${point-clock.crawl.initial-delay-ms:600000}",
      fixedDelayString = "${point-clock.crawl.interval-ms:86400000}")
  public void crawlAllSources() {
    String month = governedJobs.monthWindow();
    for (String sourceRef : sources) {
      try {
        governedJobs.run(
            "point-clock-crawl",
            month + ":" + sourceRef,
            () -> {
              crawler.crawl(sourceRef);
              return JobOutcome.of(1);
            });
      } catch (RuntimeException unexpected) {
        // The crawler classifies and dead-letters its own failures and the governance records the
        // FAILED run; this guard only keeps one source's error from skipping the others.
        log.error("PointClock scheduled crawl errored for sourceRef={}", sourceRef, unexpected);
      }
    }
  }
}
