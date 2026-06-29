import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import { PinRateRequest, PinnedSellRateResponse } from './exchange.models';

/** Feature API service for the frozen sell rate (SPEC-0003). */
@Injectable({ providedIn: 'root' })
export class ExchangeService {
  private readonly http = inject(HttpClient);

  /** Pins a new sell rate. */
  pin(request: PinRateRequest): Observable<PinnedSellRateResponse> {
    return this.http.post<PinnedSellRateResponse>(`${API_BASE_URL}/exchange/pinned-rates`, request);
  }

  /** The prevailing rate for a pair (e.g. {@code USD-BRL}). */
  current(pair: string): Observable<PinnedSellRateResponse> {
    const params = new HttpParams().set('pair', pair);
    return this.http.get<PinnedSellRateResponse>(`${API_BASE_URL}/exchange/pinned-rates/current`, {
      params,
    });
  }

  /** The pinning history for a pair, newest first. */
  history(pair: string): Observable<PageResponse<PinnedSellRateResponse>> {
    const params = new HttpParams().set('pair', pair);
    return this.http.get<PageResponse<PinnedSellRateResponse>>(`${API_BASE_URL}/exchange/pinned-rates`, {
      params,
    });
  }
}
