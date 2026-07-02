import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import {
  InboundQuarantineStatus,
  InboundQuarantineView,
  RegisterSourcedOfferRequest,
  SourcedOfferView,
} from './sourcing.models';

/** Feature API service for sourced offers and the inbound quarantine (SPEC-0009). */
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

  /** Lists the inbound quarantine (BR10/DL-0120), optionally filtered by status. */
  listQuarantine(status?: InboundQuarantineStatus): Observable<InboundQuarantineView[]> {
    const query = status ? `?status=${status}` : '';
    return this.http.get<InboundQuarantineView[]>(
      `${API_BASE_URL}/sourcing/inbound-quarantine${query}`,
    );
  }

  /** Replays a quarantined inbound payload after the cause was fixed (BR10/DL-0120). */
  replayQuarantine(id: string): Observable<InboundQuarantineView> {
    return this.http.post<InboundQuarantineView>(
      `${API_BASE_URL}/sourcing/inbound-quarantine/${id}/replay`,
      null,
    );
  }

  /** Discards a quarantined inbound payload (BR10/DL-0120). */
  discardQuarantine(id: string): Observable<InboundQuarantineView> {
    return this.http.post<InboundQuarantineView>(
      `${API_BASE_URL}/sourcing/inbound-quarantine/${id}/discard`,
      null,
    );
  }
}
