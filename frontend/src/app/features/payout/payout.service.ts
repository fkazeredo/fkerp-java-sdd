import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import {
  CreatePayoutRequest,
  ExecutePayoutRequest,
  PayoutKind,
  PayoutStatus,
  PayoutView,
} from './payout.models';

/** Optional filters for the payouts list (SPEC-0017). */
export interface PayoutFilter {
  kind?: PayoutKind | '';
  status?: PayoutStatus | '';
  payee?: string;
}

/** Feature API service for Payout repass/settlement/refund (SPEC-0017). */
@Injectable({ providedIn: 'root' })
export class PayoutService {
  private readonly http = inject(HttpClient);

  /** Lists payouts, optionally filtered. */
  list(filter: PayoutFilter = {}): Observable<PageResponse<PayoutView>> {
    let params = new HttpParams();
    if (filter.kind) {
      params = params.set('kind', filter.kind);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.payee) {
      params = params.set('payee', filter.payee);
    }
    return this.http.get<PageResponse<PayoutView>>(`${API_BASE_URL}/payouts`, { params });
  }

  /** Reads a payout (with its installments) by id. */
  getById(id: string): Observable<PayoutView> {
    return this.http.get<PayoutView>(`${API_BASE_URL}/payouts/${id}`);
  }

  /** Creates a repass/settlement/refund. */
  create(request: CreatePayoutRequest): Observable<PayoutView> {
    return this.http.post<PayoutView>(`${API_BASE_URL}/payouts`, request);
  }

  /** Executes the payout (or its next installment); a provider failure lands an explicit FAILED. */
  execute(id: string, request: ExecutePayoutRequest = {}): Observable<PayoutView> {
    return this.http.post<PayoutView>(`${API_BASE_URL}/payouts/${id}/execute`, request);
  }
}
