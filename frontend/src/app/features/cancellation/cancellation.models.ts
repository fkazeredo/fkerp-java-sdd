import { Money } from '../../core/models/api.models';

/** Cancellation policy type (SPEC-0010). */
export type CancellationType = 'STANDARD' | 'ALL_SALES_FINAL' | 'CUSTOM';

/** Who bears the cost of a cancellation charge (SPEC-0010 BR5). */
export type CostBearer = 'AGENCY' | 'ACME' | 'SUPPLIER';

/** A single penalty window (SPEC-0010 BR2). */
export interface PenaltyWindow {
  /** Upper bound in hours-until-service this window applies to (non-negative). */
  hoursBefore: number;
  /** Penalty fraction in [0, 1] (e.g. 0.50 for 50%). */
  penaltyPct: number;
}

/** Read view of the administered cancellation/no-show policy (SPEC-0010). */
export interface CancellationPolicyView {
  scopeRef: string;
  type: CancellationType;
  windows: PenaltyWindow[];
  refundable: boolean;
  costBearer: CostBearer;
  merchantOfRecord: boolean;
  noShowFee: Money | null;
  waivedIfFlightCancelled: boolean;
}

/** Body for `PUT /api/products/{ref}/cancellation-policy`. */
export interface CancellationPolicyRequest {
  type: CancellationType;
  windows: PenaltyWindow[];
  refundable: boolean;
  costBearer: CostBearer;
  merchantOfRecord?: boolean;
  noShowFee?: Money | null;
  waivedIfFlightCancelled?: boolean;
}
