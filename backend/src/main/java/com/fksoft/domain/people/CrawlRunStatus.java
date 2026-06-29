package com.fksoft.domain.people;

/**
 * Lifecycle of a crawl-run record (SPEC-0012 BR7; DL-0031). A run starts {@code RUNNING}; on
 * success it becomes {@code SUCCEEDED}; on a retryable failure that has not exhausted attempts it
 * is {@code RETRY_SCHEDULED}; once attempts are exhausted (or the failure is fatal) it is {@code
 * DEAD_LETTER} (the persisted failure state of the queue, never a misleading success).
 */
public enum CrawlRunStatus {
  RUNNING,
  SUCCEEDED,
  RETRY_SCHEDULED,
  DEAD_LETTER
}
