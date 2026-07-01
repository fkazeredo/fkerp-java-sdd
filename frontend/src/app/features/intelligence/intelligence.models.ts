import { Money } from '../../core/models/api.models';

/** The kind of insight the DSS produces (SPEC-0013). */
export type InsightType = 'PROMO_FX_ADVISOR' | 'OVERRIDE_NUDGE';

/** The axis an insight is about (SPEC-0013 BR1). */
export type SubjectKind = 'AGENCY' | 'ROUTE' | 'PRODUCT' | 'SUPPLIER';

/** Lifecycle of the human decision on an insight (SPEC-0013 BR4). */
export type InsightStatus = 'NEW' | 'ACCEPTED' | 'REJECTED' | 'DISMISSED';

/** The PromoFxAdvisor verdict (SPEC-0013 BR5). */
export type Verdict = 'CONVERTE' | 'QUEIMA_MARGEM';

/** The human decision recorded on an insight (SPEC-0013 BR4). */
export type InsightDecision = 'ACCEPTED' | 'REJECTED' | 'DISMISSED';

/** The evidence behind an insight (SPEC-0013 BR1): numbers + provenance. */
export interface InsightEvidence {
  accruedSubsidy: Money;
  realizedGap: Money;
  volumeAttracted: number;
  sources: string[];
}

/** The recommendation an insight carries (SPEC-0013 BR1). */
export interface InsightRecommendation {
  verdict: Verdict;
  action: string;
  estimatedGain: Money | null;
  estimatedRisk: Money | null;
}

/** The guardrail an insight may carry (SPEC-0013 BR1/BR3): an alert, never a block. */
export interface InsightGuardrail {
  description: string;
  thresholdCrossed: Money;
}

/** Read view of an insight (SPEC-0013). */
export interface InsightView {
  id: string;
  type: InsightType;
  subjectKind: SubjectKind;
  subjectRef: string;
  evidence: InsightEvidence;
  recommendation: InsightRecommendation;
  guardrail: InsightGuardrail | null;
  status: InsightStatus;
  generatedAt: string;
  decidedBy: string | null;
  decidedAt: string | null;
}

/** Body for `POST /api/intelligence/insights/{id}/decision`. */
export interface DecideInsightRequest {
  decision: InsightDecision;
  note?: string | null;
}
