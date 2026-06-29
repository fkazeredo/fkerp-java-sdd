import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import { AccountResponse, AccountStatus, CreateAccountRequest } from './accounts.models';

/** Feature API service for commercial accounts (SPEC-0002). */
@Injectable({ providedIn: 'root' })
export class AccountsService {
  private readonly http = inject(HttpClient);

  /** Registers a new account. */
  create(request: CreateAccountRequest): Observable<AccountResponse> {
    return this.http.post<AccountResponse>(`${API_BASE_URL}/accounts`, request);
  }

  /** Lists accounts, optionally filtered by status. */
  list(status?: AccountStatus | ''): Observable<PageResponse<AccountResponse>> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<AccountResponse>>(`${API_BASE_URL}/accounts`, { params });
  }
}
