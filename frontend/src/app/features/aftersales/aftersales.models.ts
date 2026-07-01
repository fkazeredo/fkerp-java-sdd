import { Money } from '../../core/models/api.models';

/** Kind of after-sales case (SPEC-0018 Scope). */
export type SupportCaseType =
  | 'COMPLAINT'
  | 'CHANGE_REQUEST'
  | 'CANCELLATION_REQUEST'
  | 'REFUND_REQUEST'
  | 'INFO';

/** SupportCase lifecycle state (SPEC-0018). */
export type SupportCaseStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING' | 'RESOLVED' | 'CLOSED';

/** Resolution outcome of a case (SPEC-0018 /resolve; DL-0054). */
export type CaseResolution =
  | 'REFUND_APPROVED'
  | 'CANCEL_APPROVED'
  | 'RESOLVED_NO_ACTION'
  | 'REJECTED';

/** Read view of a support case (SPEC-0018). */
export interface SupportCaseView {
  id: string;
  bookingId: string;
  type: SupportCaseType;
  status: SupportCaseStatus;
  summary: string | null;
  openedAt: string;
  firstResponseDueAt: string;
  dueAt: string;
  breached: boolean;
  resolvedAt: string | null;
  resolution: CaseResolution | null;
  linkedPayoutId: string | null;
  reopenCount: number;
  costToServeTotal: Money;
}

/** Body for `POST /api/aftersales/cases`. */
export interface OpenCaseRequest {
  bookingId: string;
  type: SupportCaseType;
  summary?: string | null;
}

/** Body for `POST /api/aftersales/cases/{id}/resolve` (SPEC-0018; DL-0054). */
export interface ResolveCaseRequest {
  resolution: CaseResolution;
  amount?: Money | null;
  handlingCost?: Money | null;
  serviceStartsAt?: string | null;
  cancellationReason?: string | null;
}
