package com.fksoft.domain.people.internal;

import com.fksoft.domain.people.CrawlRunStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link PointCrawlRun}. Module-internal: only the People module uses it. */
public interface PointCrawlRunRepository extends JpaRepository<PointCrawlRun, UUID> {

  /** Runs of a given status, newest first (history view, BR7). */
  Page<PointCrawlRun> findByStatusOrderByStartedAtDesc(CrawlRunStatus status, Pageable pageable);

  /** All runs, newest first (history view, BR7). */
  Page<PointCrawlRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
