import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api';
import {
  AttributionView,
  CampaignSendResult,
  CampaignView,
  ConsentPurpose,
  ConsentStateResponse,
  ConsentView,
  CreateCampaignRequest,
  DefineSegmentRequest,
  ErasureRequest,
  ErasureResult,
  GrantConsentRequest,
  RegisterAttributionRequest,
  SegmentPreviewResponse,
  SegmentView,
  SubjectType,
} from './marketing.models';

/** Feature API service for the Marketing module (SPEC-0019). */
@Injectable({ providedIn: 'root' })
export class MarketingService {
  private readonly http = inject(HttpClient);

  // --- Consents (BR1/LGPD) ---

  /** Reads the current consent state + append-only history for a subject/purpose. */
  consentState(
    subject: string,
    subjectType: SubjectType,
    purpose: ConsentPurpose = 'NEWSLETTER',
  ): Observable<ConsentStateResponse> {
    const params = new HttpParams()
      .set('subject', subject)
      .set('subjectType', subjectType)
      .set('purpose', purpose);
    return this.http.get<ConsentStateResponse>(`${API_BASE_URL}/marketing/consents`, { params });
  }

  /** Grants a consent (appends a GRANTED row). */
  grantConsent(request: GrantConsentRequest): Observable<ConsentView> {
    return this.http.post<ConsentView>(`${API_BASE_URL}/marketing/consents`, request);
  }

  /** Revokes a consent by row id (appends a REVOKED row). */
  revokeConsent(id: string): Observable<ConsentView> {
    return this.http.delete<ConsentView>(`${API_BASE_URL}/marketing/consents/${id}`);
  }

  // --- Segments (BR3) ---

  /** Defines a segment with validated criteria. */
  defineSegment(request: DefineSegmentRequest): Observable<SegmentView> {
    return this.http.post<SegmentView>(`${API_BASE_URL}/marketing/segments`, request);
  }

  /** Previews a segment's estimated reach (consented, reachable subjects). */
  previewSegment(id: string): Observable<SegmentPreviewResponse> {
    return this.http.get<SegmentPreviewResponse>(
      `${API_BASE_URL}/marketing/segments/${id}/preview`,
    );
  }

  // --- Campaigns (BR2/BR4) ---

  /** Creates a campaign targeting a segment. */
  createCampaign(request: CreateCampaignRequest): Observable<CampaignView> {
    return this.http.post<CampaignView>(`${API_BASE_URL}/marketing/campaigns`, request);
  }

  /** Reads a campaign by id. */
  getCampaign(id: string): Observable<CampaignView> {
    return this.http.get<CampaignView>(`${API_BASE_URL}/marketing/campaigns/${id}`);
  }

  /** Dispatches a campaign (filters by consent; idempotent per recipient). */
  sendCampaign(id: string): Observable<CampaignSendResult> {
    return this.http.post<CampaignSendResult>(
      `${API_BASE_URL}/marketing/campaigns/${id}/send`,
      {},
    );
  }

  // --- Attribution (BR5) ---

  /** Registers a campaign→booking attribution. */
  registerAttribution(request: RegisterAttributionRequest): Observable<AttributionView> {
    return this.http.post<AttributionView>(`${API_BASE_URL}/marketing/attribution`, request);
  }

  /** Lists attributions for a campaign code. */
  attribution(campaignCode: string): Observable<AttributionView[]> {
    const params = new HttpParams().set('campaignCode', campaignCode);
    return this.http.get<AttributionView[]>(`${API_BASE_URL}/marketing/attribution`, { params });
  }

  // --- LGPD erasure (BR6) ---

  /** Erases a subject's marketing PII (revocation tombstone preserved). */
  erase(request: ErasureRequest): Observable<ErasureResult> {
    return this.http.post<ErasureResult>(`${API_BASE_URL}/marketing/erasure`, request);
  }
}
