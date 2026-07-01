import { Money } from '../../core/models/api.models';

/** Kind of an internal asset (SPEC-0021). */
export type AssetType = 'EQUIPMENT' | 'SOFTWARE_LICENSE' | 'OTHER';

/** Lifecycle status of an internal asset (SPEC-0021). */
export type AssetStatus = 'ACTIVE' | 'RETIRED';

/** Read view of an internal asset (SPEC-0021). */
export interface AssetView {
  id: string;
  type: AssetType;
  identifier: string;
  status: AssetStatus;
  acquisitionDate: string;
  acquisitionCost: Money | null;
  expiresAt: string | null;
  supplierRef: string | null;
  documentId: string | null;
  financeEntryId: string | null;
  retiredAt: string | null;
  retiredBy: string | null;
  retirementReason: string | null;
  createdAt: string;
}

/** A money value in a request. */
export interface MoneyValue {
  amount: number;
  currency: string;
}

/** Body for `POST /api/assets` (SPEC-0021 BR1). */
export interface RegisterAssetRequest {
  type: AssetType;
  identifier: string;
  acquisitionDate: string;
  acquisitionCost: MoneyValue;
  expiresAt?: string | null;
  supplierRef?: string | null;
  documentId?: string | null;
  financeEntryId?: string | null;
}

/** Body for `POST /api/assets/{id}/retire` (SPEC-0021 BR4). */
export interface RetireAssetRequest {
  reason: string;
}

/** Result of the license-expiry sweep: how many licenses were newly flagged. */
export interface ExpiringSweepResponse {
  flagged: number;
}
