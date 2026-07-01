import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import {
  AuditType,
  CertificateView,
  JobRunView,
  JobStatus,
  ScheduledJobView,
  SystemAuditView,
} from './platform.models';

/**
 * Feature API service for the Platform/TI module (SPEC-0023): the governed job catalog + run history +
 * manual trigger, the e-CNPJ certificate status (METADATA ONLY — the key/password are never returned)
 * and the consolidated system audit. Job trigger and certificate import require ROLE_IT; the backend
 * is the authority, so a caller without it gets a 403 rendered as the permission state.
 */
@Injectable({ providedIn: 'root' })
export class PlatformService {
  private readonly http = inject(HttpClient);

  // --- Job governance ---

  /** The governed job catalog. */
  jobs(): Observable<ScheduledJobView[]> {
    return this.http.get<ScheduledJobView[]>(`${API_BASE_URL}/platform/jobs`);
  }

  /** The run history, filterable by job/status (paginated). */
  runs(job?: string, status?: JobStatus | ''): Observable<PageResponse<JobRunView>> {
    let params = new HttpParams();
    if (job) {
      params = params.set('job', job);
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<JobRunView>>(`${API_BASE_URL}/platform/jobs/runs`, {
      params,
    });
  }

  /** Manually triggers a governed job by name (ROLE_IT). Returns the recorded run. */
  trigger(name: string): Observable<JobRunView> {
    return this.http.post<JobRunView>(
      `${API_BASE_URL}/platform/jobs/${encodeURIComponent(name)}/trigger`,
      {},
    );
  }

  // --- e-CNPJ certificate custody (metadata only) ---

  /** The current certificate status — metadata only (BR1). 404 when none is custodied. */
  certificateStatus(): Observable<CertificateView> {
    return this.http.get<CertificateView>(`${API_BASE_URL}/platform/certificate/status`);
  }

  // --- System audit ---

  /** The filtered, paginated system-audit trail (newest first). */
  audit(
    actor?: string,
    type?: AuditType | '',
  ): Observable<PageResponse<SystemAuditView>> {
    let params = new HttpParams();
    if (actor) {
      params = params.set('actor', actor);
    }
    if (type) {
      params = params.set('type', type);
    }
    return this.http.get<PageResponse<SystemAuditView>>(`${API_BASE_URL}/platform/audit`, {
      params,
    });
  }
}
