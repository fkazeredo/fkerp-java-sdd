import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import { ReconciliationCaseView, SettlementRequest } from './reconciliation.models';

/** Feature API service for reconciliation (SPEC-0007). */
@Injectable({ providedIn: 'root' })
export class ReconciliationService {
  private readonly http = inject(HttpClient);

  /** Lists cases ordered by discrepancy (desc). */
  list(): Observable<PageResponse<ReconciliationCaseView>> {
    return this.http.get<PageResponse<ReconciliationCaseView>>(`${API_BASE_URL}/reconciliation`);
  }

  /** Records (partial or full) realized settlement values. */
  settle(caseId: string, request: SettlementRequest): Observable<ReconciliationCaseView> {
    return this.http.post<ReconciliationCaseView>(
      `${API_BASE_URL}/reconciliation/${caseId}/settlement`,
      request,
    );
  }
}
