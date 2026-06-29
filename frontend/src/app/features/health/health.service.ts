import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { SystemHealth } from './health.models';

/** Feature API service for the system health endpoint. */
@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);

  /** Calls `GET /api/system/health`. Errors surface as the normalized ApiError (error interceptor). */
  getHealth(): Observable<SystemHealth> {
    return this.http.get<SystemHealth>(`${API_BASE_URL}/system/health`);
  }
}
