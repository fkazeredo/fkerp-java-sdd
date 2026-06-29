import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { ComposeQuoteRequest, OverrideQuoteRequest, QuoteView } from './quoting.models';

/** Feature API service for quotes (SPEC-0005). */
@Injectable({ providedIn: 'root' })
export class QuotingService {
  private readonly http = inject(HttpClient);

  /** Composes a MANUAL quote. */
  compose(request: ComposeQuoteRequest): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${API_BASE_URL}/quotes`, request);
  }

  /** Applies a price override with a mandatory reason. */
  override(id: string, request: OverrideQuoteRequest): Observable<QuoteView> {
    return this.http.post<QuoteView>(`${API_BASE_URL}/quotes/${id}/override`, request);
  }
}
