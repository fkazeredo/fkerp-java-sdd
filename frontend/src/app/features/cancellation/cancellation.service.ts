import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { CancellationPolicyRequest, CancellationPolicyView } from './cancellation.models';

/** Feature API service for the cancellation/no-show policy per product scope (SPEC-0010). */
@Injectable({ providedIn: 'root' })
export class CancellationService {
  private readonly http = inject(HttpClient);

  /** Reads the cancellation policy for a product/supplier scope reference. */
  get(ref: string): Observable<CancellationPolicyView> {
    return this.http.get<CancellationPolicyView>(
      `${API_BASE_URL}/products/${encodeURIComponent(ref)}/cancellation-policy`,
    );
  }

  /** Administers (upserts) the cancellation policy for a scope reference. */
  put(ref: string, request: CancellationPolicyRequest): Observable<CancellationPolicyView> {
    return this.http.put<CancellationPolicyView>(
      `${API_BASE_URL}/products/${encodeURIComponent(ref)}/cancellation-policy`,
      request,
    );
  }
}
