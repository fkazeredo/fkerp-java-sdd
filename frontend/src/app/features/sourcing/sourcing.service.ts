import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import { RegisterSourcedOfferRequest, SourcedOfferView } from './sourcing.models';

/** Feature API service for sourced offers (SPEC-0009). */
@Injectable({ providedIn: 'root' })
export class SourcingService {
  private readonly http = inject(HttpClient);

  /** Registers a sourced offer's provenance. */
  register(request: RegisterSourcedOfferRequest): Observable<SourcedOfferView> {
    return this.http.post<SourcedOfferView>(`${API_BASE_URL}/sourcing/offers`, request);
  }

  /** Reads a sourced offer by id. */
  getById(id: string): Observable<SourcedOfferView> {
    return this.http.get<SourcedOfferView>(`${API_BASE_URL}/sourcing/offers/${id}`);
  }
}
