/** Governance layer of a parameter rule (SPEC-0014 BR1/BR2); precedence: lower rank wins. */
export type ParameterLayer =
  | 'DIRECTIVE'
  | 'PROMOTION'
  | 'CONTRACT'
  | 'POLICY'
  | 'SYSTEM_DEFAULT';

/** The type of a governed parameter's value (SPEC-0014 BR1). */
export type ParameterValueType = 'NUMBER' | 'PERCENT' | 'MONEY' | 'BOOL';

/** Provenance of a resolved parameter (which layer won, who defined it, when). */
export interface ProvenanceResponse {
  layer: string;
  ruleId: string;
  definedBy: string;
  definedAt: string;
  validUntil: string | null;
}

/** Response of `GET /api/commercial-policy/resolve`: the winning value + provenance (BR2). */
export interface ResolvedParameterResponse {
  key: string;
  value: string;
  type: string;
  provenance: ProvenanceResponse;
}

/** A governed rule projection (SPEC-0014). */
export interface ParameterRuleResponse {
  id: string;
  key: string;
  layer: string;
  accountId: string | null;
  productRef: string | null;
  channel: string | null;
  value: string;
  type: string;
  validFrom: string;
  validUntil: string | null;
  definedBy: string;
  justification: string | null;
  createdAt: string;
}

/** Body for `POST /api/commercial-policy/rules` (POLICY/PROMOTION/CONTRACT layers). */
export interface DefineRuleRequest {
  key: string;
  layer: ParameterLayer;
  value: string;
  type: ParameterValueType;
  accountId?: string | null;
  productRef?: string | null;
  channel?: string | null;
  validFrom?: string | null;
  validUntil?: string | null;
}

/** Body for `POST /api/commercial-policy/directives` (always the DIRECTIVE layer; justification). */
export interface IssueDirectiveRequest {
  key: string;
  value: string;
  type: ParameterValueType;
  accountId?: string | null;
  productRef?: string | null;
  channel?: string | null;
  validFrom?: string | null;
  validUntil?: string | null;
  justification: string;
}
