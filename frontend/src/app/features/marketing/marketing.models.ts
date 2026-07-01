/** The kind of marketing/consent subject (SPEC-0019 BR1). */
export type SubjectType = 'ACCOUNT' | 'AGENT';

/** The purpose a consent is granted/revoked for (SPEC-0019 BR1). */
export type ConsentPurpose = 'NEWSLETTER';

/** The status of a consent decision (SPEC-0019 BR1). */
export type ConsentStatus = 'GRANTED' | 'REVOKED';

/** The LGPD legal basis recorded with a consent (SPEC-0019 BR1). */
export type LegalBasis = 'CONSENT' | 'LEGITIMATE_INTEREST';

/** Campaign lifecycle status (SPEC-0019). */
export type CampaignStatus = 'DRAFT' | 'SENT';

/** A subject referenced by value (id + type). */
export interface SubjectRef {
  id: string;
  type: SubjectType;
}

/** A single consent log row (SPEC-0019). */
export interface ConsentView {
  id: string;
  subjectId: string;
  subjectType: SubjectType;
  purpose: ConsentPurpose;
  legalBasis: LegalBasis;
  status: ConsentStatus;
  source: string | null;
  createdAt: string;
}

/** The current consent state (projection of the latest row). */
export interface ConsentState {
  subject: SubjectRef;
  purpose: ConsentPurpose;
  currentStatus: ConsentStatus;
  lastChangedAt: string;
}

/** Response of `GET /api/marketing/consents`: current state + append-only history. */
export interface ConsentStateResponse {
  current: ConsentState;
  history: ConsentView[];
}

/** Read view of a segment (SPEC-0019). */
export interface SegmentView {
  id: string;
  name: string;
  criteria: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

/** Response of `GET /api/marketing/segments/{id}/preview`: the estimated reach (BR3). */
export interface SegmentPreviewResponse {
  segmentId: string;
  reachable: number;
}

/** Read view of a campaign (SPEC-0019). */
export interface CampaignView {
  id: string;
  segmentId: string;
  code: string;
  contentRef: string | null;
  windowFrom: string | null;
  windowTo: string | null;
  status: CampaignStatus;
  createdAt: string;
}

/** The outcome of dispatching a campaign (SPEC-0019). */
export interface CampaignSendResult {
  campaignId: string;
  targeted: number;
  suppressedNoConsent: number;
  queued: number;
}

/** Read view of a campaign→booking attribution (SPEC-0019 BR5). */
export interface AttributionView {
  id: string;
  campaignCode: string;
  bookingId: string;
  converted: boolean;
  attributedAt: string;
  convertedAt: string | null;
}

/** The outcome of an LGPD erasure request (SPEC-0019 BR6). */
export interface ErasureResult {
  subjectId: string;
  anonymizedConsents: number;
  suppressed: boolean;
}

/** Body for `POST /api/marketing/consents` (SPEC-0019 BR1). */
export interface GrantConsentRequest {
  subject: SubjectRef;
  purpose: ConsentPurpose;
  legalBasis?: LegalBasis | null;
  source?: string | null;
}

/** Body for `POST /api/marketing/segments` (SPEC-0019 BR3). */
export interface DefineSegmentRequest {
  name: string;
  criteria: Record<string, string>;
}

/** Body for `POST /api/marketing/campaigns` (SPEC-0019). */
export interface CreateCampaignRequest {
  segmentId: string;
  code: string;
  contentRef?: string | null;
  windowFrom?: string | null;
  windowTo?: string | null;
}

/** Body for `POST /api/marketing/attribution` (SPEC-0019 BR5). */
export interface RegisterAttributionRequest {
  campaignCode: string;
  bookingId: string;
}

/** Body for `POST /api/marketing/erasure` (SPEC-0019 BR6). */
export interface ErasureRequest {
  subjectId: string;
  subjectType: SubjectType;
}
