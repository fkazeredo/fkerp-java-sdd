package com.fksoft.application.api;

import com.fksoft.domain.platform.JobRunView;
import com.fksoft.domain.platform.JobStatus;
import com.fksoft.domain.platform.PlatformJobService;
import com.fksoft.domain.platform.ScheduledJobView;
import com.fksoft.infra.jobs.GovernedJobs;
import com.fksoft.infra.web.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for job governance (SPEC-0023) — slice 8j-2: the job catalog, the run history
 * (filterable by job/status, paginated) and the manual trigger. The trigger validates the job
 * against the catalog (404 when unknown) and dispatches it through the Platform governance (the
 * lock, idempotency and {@code JobRun} history apply); a job already running yields {@code 409}
 * (locked). It responds {@code 202 Accepted} with the recorded run.
 */
@RestController
@RequestMapping("/api/platform/jobs")
@RequiredArgsConstructor
public class PlatformJobController {

  private final PlatformJobService jobService;
  private final GovernedJobs governedJobs;

  /** The job catalog (SPEC-0023 — {@code GET /jobs}). */
  @GetMapping
  public List<ScheduledJobView> jobs() {
    return jobService.catalog();
  }

  /**
   * The run history, filterable by job and status, paginated (SPEC-0023 — {@code GET /jobs/runs}).
   */
  @GetMapping("/runs")
  public PageResponse<JobRunView> runs(
      @RequestParam(required = false) String job,
      @RequestParam(required = false) JobStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    return PageResponse.from(jobService.runs(job, status, pageable));
  }

  /**
   * Manually triggers a job (TI role) — SPEC-0023 {@code POST /jobs/{name}/trigger}. Validates the
   * job exists, runs it under governance and returns {@code 202 Accepted} with the recorded run. A
   * job already running yields {@code 409} (locked); an unknown job yields {@code 404}.
   */
  @PostMapping("/{name}/trigger")
  public ResponseEntity<JobRunView> trigger(@PathVariable String name) {
    jobService.requireJob(name); // 404 if the job is not in the catalog
    JobRunView run = governedJobs.run(name);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(run);
  }
}
