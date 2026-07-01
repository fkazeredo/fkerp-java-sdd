import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import {
  CreateLedgerEntryRequest,
  EntryStatus,
  LedgerDirection,
  LedgerEntryView,
  PeriodView,
  TrialBalanceView,
} from './finance.models';

/** Optional filters for the ledger entries list (SPEC-0015). */
export interface LedgerEntryFilter {
  direction?: LedgerDirection | '';
  status?: EntryStatus | '';
  period?: string;
  party?: string;
}

/** Feature API service for the Finance ledger and period close (SPEC-0015). */
@Injectable({ providedIn: 'root' })
export class FinanceService {
  private readonly http = inject(HttpClient);

  /** Lists AP/AR ledger entries, optionally filtered. */
  listEntries(filter: LedgerEntryFilter = {}): Observable<PageResponse<LedgerEntryView>> {
    let params = new HttpParams();
    if (filter.direction) {
      params = params.set('direction', filter.direction);
    }
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.period) {
      params = params.set('period', filter.period);
    }
    if (filter.party) {
      params = params.set('party', filter.party);
    }
    return this.http.get<PageResponse<LedgerEntryView>>(`${API_BASE_URL}/finance/entries`, {
      params,
    });
  }

  /** Registers a new ledger entry. */
  createEntry(request: CreateLedgerEntryRequest): Observable<LedgerEntryView> {
    return this.http.post<LedgerEntryView>(`${API_BASE_URL}/finance/entries`, request);
  }

  /** Reads an accounting period by `YYYY-MM`. */
  getPeriod(yyyymm: string): Observable<PeriodView> {
    return this.http.get<PeriodView>(`${API_BASE_URL}/finance/periods/${yyyymm}`);
  }

  /** Reads the trial balance (per currency) of a period. */
  trialBalance(yyyymm: string): Observable<TrialBalanceView> {
    return this.http.get<TrialBalanceView>(
      `${API_BASE_URL}/finance/periods/${yyyymm}/trial-balance`,
    );
  }

  /** Closes a period (respects the Compliance veto; fails with `finance.period.cannot-close`). */
  closePeriod(yyyymm: string): Observable<PeriodView> {
    return this.http.post<PeriodView>(`${API_BASE_URL}/finance/periods/${yyyymm}/close`, {});
  }
}
