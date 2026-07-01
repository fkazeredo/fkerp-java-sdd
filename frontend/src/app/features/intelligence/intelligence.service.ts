import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import {
  DecideInsightRequest,
  InsightStatus,
  InsightType,
  InsightView,
} from './intelligence.models';

/** Optional filters for the insight list (SPEC-0013). */
export interface InsightFilter {
  type?: InsightType | '';
  subjectRef?: string;
  status?: InsightStatus | '';
}

/** Feature API service for the Intelligence/DSS insights (SPEC-0013). */
@Injectable({ providedIn: 'root' })
export class IntelligenceService {
  private readonly http = inject(HttpClient);

  /** Lists insights, optionally filtered (ordered by estimated gain). */
  list(filter: InsightFilter = {}): Observable<PageResponse<InsightView>> {
    let params = new HttpParams();
    if (filter.type) {
      params = params.set('type', filter.type);
    }
    if (filter.subjectRef) {
      params = params.set('subjectRef', filter.subjectRef);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    return this.http.get<PageResponse<InsightView>>(`${API_BASE_URL}/intelligence/insights`, {
      params,
    });
  }

  /** Reads an insight by id. */
  getById(id: string): Observable<InsightView> {
    return this.http.get<InsightView>(`${API_BASE_URL}/intelligence/insights/${id}`);
  }

  /** Records the human decision on an insight (records only — never acts — BR2). */
  decide(id: string, request: DecideInsightRequest): Observable<InsightView> {
    return this.http.post<InsightView>(
      `${API_BASE_URL}/intelligence/insights/${id}/decision`,
      request,
    );
  }
}
