import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import {
  OpenCaseRequest,
  ResolveCaseRequest,
  SupportCaseStatus,
  SupportCaseType,
  SupportCaseView,
} from './aftersales.models';

/** Optional filters for the support-case list (SPEC-0018). */
export interface SupportCaseFilter {
  type?: SupportCaseType | '';
  status?: SupportCaseStatus | '';
  bookingId?: string;
  breached?: boolean | null;
}

/** Feature API service for the AfterSales support cases (SPEC-0018). */
@Injectable({ providedIn: 'root' })
export class AfterSalesService {
  private readonly http = inject(HttpClient);

  /** Lists support cases, optionally filtered (newest first). */
  list(filter: SupportCaseFilter = {}): Observable<PageResponse<SupportCaseView>> {
    let params = new HttpParams();
    if (filter.type) {
      params = params.set('type', filter.type);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.bookingId) {
      params = params.set('bookingId', filter.bookingId);
    }
    if (filter.breached != null) {
      params = params.set('breached', String(filter.breached));
    }
    return this.http.get<PageResponse<SupportCaseView>>(`${API_BASE_URL}/aftersales/cases`, {
      params,
    });
  }

  /** Reads a case by id. */
  getById(id: string): Observable<SupportCaseView> {
    return this.http.get<SupportCaseView>(`${API_BASE_URL}/aftersales/cases/${id}`);
  }

  /** Opens a new support case referencing a booking. */
  open(request: OpenCaseRequest): Observable<SupportCaseView> {
    return this.http.post<SupportCaseView>(`${API_BASE_URL}/aftersales/cases`, request);
  }

  /** Drives a lifecycle transition (assign/progress/wait/close). */
  transition(
    id: string,
    action: 'assign' | 'progress' | 'wait' | 'close',
  ): Observable<SupportCaseView> {
    return this.http.post<SupportCaseView>(
      `${API_BASE_URL}/aftersales/cases/${id}/${action}`,
      {},
    );
  }

  /** Resolves a case (may trigger a Booking cancellation and/or a Payout REFUND). */
  resolve(id: string, request: ResolveCaseRequest): Observable<SupportCaseView> {
    return this.http.post<SupportCaseView>(
      `${API_BASE_URL}/aftersales/cases/${id}/resolve`,
      request,
    );
  }
}
