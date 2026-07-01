import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import {
  AdminContractView,
  AdminExpenseView,
  AdminSupplierStatus,
  AdminSupplierType,
  AdminSupplierView,
  ExpiringSweepResponse,
  RegisterContractRequest,
  RegisterExpenseRequest,
  RegisterSupplierRequest,
} from './admin.models';

/**
 * Feature API service for the Admin module (SPEC-0025 — administrative suppliers/contracts/expenses).
 * The write endpoints require ROLE_FINANCE (gated in SecurityConfig, DL-0088); the backend is the
 * authority, so a caller without the role gets a 403 rendered as the permission state.
 */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  // --- Suppliers (BR1) ---

  /** Lists administrative suppliers, optionally filtered by type/status. */
  listSuppliers(
    type?: AdminSupplierType | '',
    status?: AdminSupplierStatus | '',
  ): Observable<AdminSupplierView[]> {
    let params = new HttpParams();
    if (type) {
      params = params.set('type', type);
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<AdminSupplierView[]>(`${API_BASE_URL}/admin/suppliers`, { params });
  }

  /** Reads a supplier by id. */
  getSupplier(id: string): Observable<AdminSupplierView> {
    return this.http.get<AdminSupplierView>(`${API_BASE_URL}/admin/suppliers/${id}`);
  }

  /** Registers an administrative supplier (ROLE_FINANCE). */
  registerSupplier(request: RegisterSupplierRequest): Observable<AdminSupplierView> {
    return this.http.post<AdminSupplierView>(`${API_BASE_URL}/admin/suppliers`, request);
  }

  // --- Contracts (BR2) ---

  /** Lists a supplier's administrative contracts. */
  contracts(supplierId: string): Observable<AdminContractView[]> {
    return this.http.get<AdminContractView[]>(
      `${API_BASE_URL}/admin/suppliers/${supplierId}/contracts`,
    );
  }

  /** Registers an administrative contract for a supplier (ROLE_FINANCE). */
  registerContract(
    supplierId: string,
    request: RegisterContractRequest,
  ): Observable<AdminContractView> {
    return this.http.post<AdminContractView>(
      `${API_BASE_URL}/admin/suppliers/${supplierId}/contracts`,
      request,
    );
  }

  // --- Recurring expenses (BR3) ---

  /** Registers a recurring administrative expense (creates the Finance ledger entry) (ROLE_FINANCE). */
  registerExpense(request: RegisterExpenseRequest): Observable<AdminExpenseView> {
    return this.http.post<AdminExpenseView>(`${API_BASE_URL}/admin/expenses`, request);
  }

  // --- Contract-expiry alert sweep (BR5) ---

  /** Runs the contract-expiry sweep, flagging contracts about to expire (ROLE_FINANCE). */
  flagExpiring(): Observable<ExpiringSweepResponse> {
    return this.http.post<ExpiringSweepResponse>(
      `${API_BASE_URL}/admin/contracts/flag-expiring`,
      {},
    );
  }
}
