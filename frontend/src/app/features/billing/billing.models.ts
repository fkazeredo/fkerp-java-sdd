import { Money } from '../../core/models/api.models';

/** Lifecycle of a commission invoice (SPEC-0016). */
export type InvoiceStatus = 'RASCUNHO' | 'EMITIDA' | 'CANCELADA';
/** Tax regime of the issuer (SPEC-0016 Q7, DL-0044). */
export type TaxRegime = 'SIMPLES_NACIONAL' | 'LUCRO_PRESUMIDO' | 'LUCRO_REAL';
/** Kind of tax withholding (SPEC-0016 BR2). */
export type WithholdingKind = 'IRRF' | 'PIS' | 'COFINS' | 'CSLL' | 'ISS_RETIDO';

/** A single withholding line of an assessment (SPEC-0016 BR2). */
export interface Withholding {
  kind: WithholdingKind;
  amount: Money;
}

/** Commission invoice view returned by the backend (SPEC-0016). */
export interface CommissionInvoiceView {
  id: string;
  commissionEntryId: string;
  base: Money;
  status: InvoiceStatus;
  iss: Money | null;
  withholdings: Withholding[];
  regime: TaxRegime | null;
  municipality: string;
  serviceCode: string | null;
  number: string | null;
  verificationCode: string | null;
  documentId: string | null;
  createdAt: string;
}

/** Body for `POST /api/billing/invoices` (SPEC-0016 BR1: base = commission, not the gross package). */
export interface CreateCommissionInvoiceRequest {
  commissionEntryId: string;
  base: Money;
  municipality: string;
  serviceCode?: string | null;
}

/** Body for `POST /api/billing/invoices/{id}/cancel`. */
export interface CancelCommissionInvoiceRequest {
  reason: string;
}
