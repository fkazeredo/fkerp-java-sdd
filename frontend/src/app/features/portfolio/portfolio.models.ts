import { Money } from '../../core/models/api.models';

/** Lifecycle status of a represented brand (SPEC-0020 BR1). */
export type BrandStatus = 'ACTIVE' | 'INACTIVE';

/** The metric a brand goal is measured in (SPEC-0020 BR3). */
export type GoalMetric = 'VOLUME' | 'REVENUE';

/** Read view of a represented brand (SPEC-0020). */
export interface BrandView {
  id: string;
  brandRef: string;
  displayName: string;
  status: BrandStatus;
  createdAt: string;
  updatedAt: string;
}

/** Read view of a representation contract (SPEC-0020). */
export interface ContractView {
  id: string;
  brandRef: string;
  validFrom: string;
  validUntil: string | null;
  documentId: string | null;
  terms: Record<string, string>;
  createdAt: string;
}

/** Read-model of contract coverage on a date (SPEC-0020 BR2): an alert, never a block. */
export interface ContractCoverage {
  brandRef: string;
  on: string;
  covered: boolean;
}

/** Read view of a brand goal (SPEC-0020 BR3). */
export interface GoalView {
  id: string;
  brandRef: string;
  period: string;
  metric: GoalMetric;
  targetAmount: Money | null;
  targetCount: number | null;
  createdAt: string;
}

/** Read-model of a goal's progress: target vs realized + attainment (SPEC-0020 BR4). */
export interface GoalProgress {
  brandRef: string;
  period: string;
  metric: GoalMetric;
  targetAmount: Money | null;
  realizedAmount: Money | null;
  targetCount: number | null;
  realizedCount: number | null;
  attainmentPct: number;
}

/** Body for `POST /api/portfolio/brands` (SPEC-0020 BR1). */
export interface RegisterBrandRequest {
  brandRef: string;
  displayName: string;
}

/** Body for `POST /api/portfolio/brands/{brandRef}/contracts` (SPEC-0020 BR2). */
export interface RegisterContractRequest {
  validFrom: string;
  validUntil?: string | null;
  documentId?: string | null;
  terms?: Record<string, string> | null;
}

/** A money value in a goal request. */
export interface MoneyValue {
  amount: number;
  currency: string;
}

/** Body for `POST /api/portfolio/brands/{brandRef}/goals` (SPEC-0020 BR3). */
export interface DefineGoalRequest {
  period: string;
  metric: GoalMetric;
  target?: MoneyValue | null;
  targetCount?: number | null;
}

/** Result of the expiry sweep: how many contracts were newly flagged. */
export interface ExpiringSweepResponse {
  flagged: number;
}
