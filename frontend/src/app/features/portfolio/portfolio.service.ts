import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import {
  BrandStatus,
  BrandView,
  ContractCoverage,
  ContractView,
  DefineGoalRequest,
  ExpiringSweepResponse,
  GoalProgress,
  GoalView,
  RegisterBrandRequest,
  RegisterContractRequest,
} from './portfolio.models';

/** Feature API service for the Portfolio module (SPEC-0020). */
@Injectable({ providedIn: 'root' })
export class PortfolioService {
  private readonly http = inject(HttpClient);

  // --- Brands (BR1) ---

  /** Lists represented brands, optionally filtered by status. */
  listBrands(status?: BrandStatus | ''): Observable<BrandView[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<BrandView[]>(`${API_BASE_URL}/portfolio/brands`, { params });
  }

  /** Reads a brand by id. */
  getBrand(id: string): Observable<BrandView> {
    return this.http.get<BrandView>(`${API_BASE_URL}/portfolio/brands/${id}`);
  }

  /** Registers a represented brand. */
  registerBrand(request: RegisterBrandRequest): Observable<BrandView> {
    return this.http.post<BrandView>(`${API_BASE_URL}/portfolio/brands`, request);
  }

  /** Deactivates a brand (ends its representation). */
  deactivateBrand(id: string): Observable<BrandView> {
    return this.http.delete<BrandView>(`${API_BASE_URL}/portfolio/brands/${id}`);
  }

  // --- Representation contracts (BR2) ---

  /** Lists a brand's representation contracts. */
  contracts(brandRef: string): Observable<ContractView[]> {
    return this.http.get<ContractView[]>(
      `${API_BASE_URL}/portfolio/brands/${brandRef}/contracts`,
    );
  }

  /** Registers a representation contract for a brand. */
  registerContract(
    brandRef: string,
    request: RegisterContractRequest,
  ): Observable<ContractView> {
    return this.http.post<ContractView>(
      `${API_BASE_URL}/portfolio/brands/${brandRef}/contracts`,
      request,
    );
  }

  /** Checks whether a brand has an in-force contract on a date (read-model alert). */
  contractCoverage(brandRef: string, on?: string): Observable<ContractCoverage> {
    let params = new HttpParams();
    if (on) {
      params = params.set('on', on);
    }
    return this.http.get<ContractCoverage>(
      `${API_BASE_URL}/portfolio/brands/${brandRef}/contract-coverage`,
      { params },
    );
  }

  // --- Goals + realized projection (BR3/BR4) ---

  /** Defines a brand goal (VOLUME or REVENUE) for a period. */
  defineGoal(brandRef: string, request: DefineGoalRequest): Observable<GoalView> {
    return this.http.post<GoalView>(
      `${API_BASE_URL}/portfolio/brands/${brandRef}/goals`,
      request,
    );
  }

  /** Reads a goal's progress (target vs realized + attainment) by brand id and period. */
  goalProgress(id: string, period: string): Observable<GoalProgress> {
    return this.http.get<GoalProgress>(
      `${API_BASE_URL}/portfolio/brands/${id}/goals/${period}/progress`,
    );
  }

  // --- Expiry alert sweep (BR5) ---

  /** Runs the expiry sweep, flagging contracts that are about to expire. */
  flagExpiring(): Observable<ExpiringSweepResponse> {
    return this.http.post<ExpiringSweepResponse>(
      `${API_BASE_URL}/portfolio/contracts/flag-expiring`,
      {},
    );
  }
}
