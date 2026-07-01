import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { PageResponse } from '../../core/models/api.models';
import {
  CreateEmployeeRequest,
  DiscrepancyStatus,
  DiscrepancyView,
  EmployeeStatus,
  EmployeeView,
  JourneyView,
  ProcessJourneyRequest,
  TimeBankView,
} from './people.models';

/** Feature API service for the People/HR module (SPEC-0022). */
@Injectable({ providedIn: 'root' })
export class PeopleService {
  private readonly http = inject(HttpClient);

  // --- Collaborators (BR1) ---

  /** Lists collaborators, optionally filtered by status (paginated). */
  listEmployees(status?: EmployeeStatus | ''): Observable<PageResponse<EmployeeView>> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<EmployeeView>>(`${API_BASE_URL}/people/employees`, {
      params,
    });
  }

  /** Reads a collaborator by id. */
  getEmployee(id: string): Observable<EmployeeView> {
    return this.http.get<EmployeeView>(`${API_BASE_URL}/people/employees/${id}`);
  }

  /** Registers a collaborator. */
  registerEmployee(request: CreateEmployeeRequest): Observable<EmployeeView> {
    return this.http.post<EmployeeView>(`${API_BASE_URL}/people/employees`, request);
  }

  // --- Journey / time-bank (BR2/BR3) ---

  /** Processes a collaborator period journey. */
  processJourney(id: string, request: ProcessJourneyRequest): Observable<JourneyView> {
    return this.http.post<JourneyView>(
      `${API_BASE_URL}/people/employees/${id}/journey`,
      request,
    );
  }

  /** Reads a collaborator processed journey for a period. */
  journey(id: string, period: string): Observable<JourneyView> {
    return this.http.get<JourneyView>(`${API_BASE_URL}/people/employees/${id}/journey`, {
      params: new HttpParams().set('period', period),
    });
  }

  /** Reads the time-bank for a collaborator/period. */
  timebank(id: string, period: string): Observable<TimeBankView> {
    return this.http.get<TimeBankView>(`${API_BASE_URL}/people/employees/${id}/timebank`, {
      params: new HttpParams().set('period', period),
    });
  }

  // --- Discrepancy treatment queue (BR4) ---

  /** Browses the discrepancy queue, optionally filtered by period/status (paginated). */
  discrepancies(
    period?: string,
    status?: DiscrepancyStatus | '',
  ): Observable<PageResponse<DiscrepancyView>> {
    let params = new HttpParams();
    if (period) {
      params = params.set('period', period);
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<DiscrepancyView>>(`${API_BASE_URL}/people/discrepancies`, {
      params,
    });
  }
}
