import { Money } from '../../core/models/api.models';

export type CaseStatus = 'OPEN' | 'PARTIALLY_SETTLED' | 'SETTLED' | 'DISCREPANCY' | 'CANCELLED';

/** Reconciliation case returned by the backend (SPEC-0007). */
export interface ReconciliationCaseView {
  caseId: string;
  bookingId: string;
  baseAmount: Money;
  pinnedRate: number;
  baseBrl: Money;
  expectedSupplierCommission: Money;
  expectedAgentCommission: Money;
  expectedSpread: Money;
  realizedSpread: Money | null;
  fxGainLoss: Money | null;
  discrepancy: Money;
  status: CaseStatus;
}

/** Body for `POST /api/reconciliation/{caseId}/settlement`. */
export interface SettlementRequest {
  amountReceivedFromAgency?: Money | null;
  supplierSettlementRate?: number | null;
  supplierPaidAmount?: Money | null;
  commissionReceivedFromSupplier?: Money | null;
  commissionPaidToAgent?: Money | null;
}
