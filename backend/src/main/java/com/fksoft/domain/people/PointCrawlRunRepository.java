package com.fksoft.domain.people;

import com.fksoft.domain.ModuleInternal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link PointCrawlRun}. Module-internal: only the People module uses it. */
@ModuleInternal
public interface PointCrawlRunRepository extends JpaRepository<PointCrawlRun, UUID> {

  /** Runs of a given status, newest first (history view, BR7). */
  Page<PointCrawlRun> findByStatusOrderByStartedAtDesc(CrawlRunStatus status, Pageable pageable);

  /** All runs, newest first (history view, BR7). */
  Page<PointCrawlRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
