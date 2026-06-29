import { Money } from '../../core/models/api.models';

/** An override step in a quote's history. */
export interface OverrideRecordView {
  fromAmount: Money;
  toAmount: Money;
  reason: string;
  performedBy: string;
  performedAt: string;
}

/** Composed quote returned by the backend (SPEC-0005). */
export interface QuoteView {
  id: string;
  accountId: string;
  priceOrigin: string;
  basePrice: Money;
  fxRate: number;
  baseConverted: Money;
  commission: { supplier: Money; agent: Money; spread: Money; spreadNegative: boolean };
  markup: { pct: number; amount: Money; source: string };
  suggestedAmount: Money;
  appliedAmount: Money;
  status: string;
  validUntil: string | null;
  provenance: { rateId: string; policySource: string };
  overrides: OverrideRecordView[];
}

/** Body for `POST /api/quotes`. */
export interface ComposeQuoteRequest {
  accountId: string;
  basePrice: Money;
  currencyPair: string;
  supplierCommissionPct: number;
  agentCommissionPct: number;
  validUntil?: string | null;
}

/** Body for `POST /api/quotes/{id}/override`. */
export interface OverrideQuoteRequest {
  appliedAmount: Money;
  reason: string;
}
