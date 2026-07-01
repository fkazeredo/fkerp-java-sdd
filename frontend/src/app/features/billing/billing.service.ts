import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import {
  CancelCommissionInvoiceRequest,
  CommissionInvoiceView,
  CreateCommissionInvoiceRequest,
} from './billing.models';

/** Feature API service for the Billing commission invoice (SPEC-0016). */
@Injectable({ providedIn: 'root' })
export class BillingService {
  private readonly http = inject(HttpClient);

  /** Reads a commission invoice by id. */
  getById(id: string): Observable<CommissionInvoiceView> {
    return this.http.get<CommissionInvoiceView>(`${API_BASE_URL}/billing/invoices/${id}`);
  }

  /** Creates a draft commission invoice from a Finance commission entry. */
  createDraft(request: CreateCommissionInvoiceRequest): Observable<CommissionInvoiceView> {
    return this.http.post<CommissionInvoiceView>(`${API_BASE_URL}/billing/invoices`, request);
  }

  /** Issues the invoice (computes ISS, signs, transmits, archives) — requires ROLE_FINANCE. */
  issue(id: string): Observable<CommissionInvoiceView> {
    return this.http.post<CommissionInvoiceView>(`${API_BASE_URL}/billing/invoices/${id}/issue`, {});
  }

  /** Cancels an issued NF-e at the municipality. */
  cancel(id: string, request: CancelCommissionInvoiceRequest): Observable<CommissionInvoiceView> {
    return this.http.post<CommissionInvoiceView>(
      `${API_BASE_URL}/billing/invoices/${id}/cancel`,
      request,
    );
  }
}
