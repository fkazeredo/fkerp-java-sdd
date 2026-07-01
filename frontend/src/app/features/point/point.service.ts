import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import { CrawlRunStatus, PointCrawlRunView, PointSnapshotView } from './point.models';

/**
 * Feature API service for the operational side of point integration (SPEC-0012). Only the read
 * endpoints an operator/IT actually views are wired here: the crawl-run execution history and an
 * operational snapshot by id. The AFD/AEJ ingest and the manual crawl trigger are machine-to-machine
 * / operational actions (multipart legal ingest, HMAC ACL) — out of the operator UI scope (DL-0109).
 */
@Injectable({ providedIn: 'root' })
export class PointService {
  private readonly http = inject(HttpClient);

  /** Lists the crawl-run history, optionally filtered by status (paginated). */
  runs(status?: CrawlRunStatus | ''): Observable<PageResponse<PointCrawlRunView>> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<PointCrawlRunView>>(
      `${API_BASE_URL}/integration/point/runs`,
      { params },
    );
  }

  /** Reads an operational snapshot by id. */
  snapshot(id: string): Observable<PointSnapshotView> {
    return this.http.get<PointSnapshotView>(`${API_BASE_URL}/integration/point/snapshots/${id}`);
  }
}
