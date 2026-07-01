import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import {
  AssetStatus,
  AssetType,
  AssetView,
  ExpiringSweepResponse,
  RegisterAssetRequest,
  RetireAssetRequest,
} from './assets.models';

/** Feature API service for the Assets module (SPEC-0021 — internal patrimony). */
@Injectable({ providedIn: 'root' })
export class AssetsService {
  private readonly http = inject(HttpClient);

  /** Lists assets with combinable type/status/expiring-within-days filters. */
  list(
    type?: AssetType | '',
    status?: AssetStatus | '',
    expiringWithinDays?: number | null,
  ): Observable<AssetView[]> {
    let params = new HttpParams();
    if (type) {
      params = params.set('type', type);
    }
    if (status) {
      params = params.set('status', status);
    }
    if (expiringWithinDays != null) {
      params = params.set('expiringWithinDays', String(expiringWithinDays));
    }
    return this.http.get<AssetView[]>(`${API_BASE_URL}/assets`, { params });
  }

  /** Reads an asset by id. */
  get(id: string): Observable<AssetView> {
    return this.http.get<AssetView>(`${API_BASE_URL}/assets/${id}`);
  }

  /** Registers an internal asset. */
  register(request: RegisterAssetRequest): Observable<AssetView> {
    return this.http.post<AssetView>(`${API_BASE_URL}/assets`, request);
  }

  /** Retires an asset with an audited reason. */
  retire(id: string, request: RetireAssetRequest): Observable<AssetView> {
    return this.http.post<AssetView>(`${API_BASE_URL}/assets/${id}/retire`, request);
  }

  /** Runs the license-expiry alert sweep, flagging licenses about to expire. */
  flagExpiring(): Observable<ExpiringSweepResponse> {
    return this.http.post<ExpiringSweepResponse>(`${API_BASE_URL}/assets/flag-expiring`, {});
  }
}
