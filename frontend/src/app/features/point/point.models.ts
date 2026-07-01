/** Lifecycle status of a point-clock crawl run (SPEC-0012 BR7). */
export type CrawlRunStatus = 'RUNNING' | 'SUCCEEDED' | 'RETRY_SCHEDULED' | 'DEAD_LETTER';

/** Read view of an operational point-clock snapshot (SPEC-0012). */
export interface PointSnapshotView {
  id: string;
  sourceRef: string;
  periodRef: string;
  operationalOnly: boolean;
  marks: number;
  collectedAt: string;
}

/** Read view of a crawl-run execution record (SPEC-0012 BR7). */
export interface PointCrawlRunView {
  id: string;
  sourceRef: string;
  periodRef: string;
  status: CrawlRunStatus;
  attempts: number;
  items: number | null;
  failures: number | null;
  failureClass: string | null;
  startedAt: string;
  finishedAt: string | null;
  correlationId: string;
}
