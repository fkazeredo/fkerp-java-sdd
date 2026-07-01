import { Money } from '../../core/models/api.models';

/** The kind of payout (SPEC-0017 BR1). */
export type PayoutKind = 'AGENT_COMMISSION' | 'SUPPLIER_SETTLEMENT' | 'REFUND';
/** Lifecycle of a payout / installment (SPEC-0017 BR2). */
export type PayoutStatus = 'PENDING' | 'EXECUTING' | 'EXECUTED' | 'FAILED';
/** The kind of party a payout pays (SPEC-0017 BR1). */
export type PayeeType = 'AGENT' | 'SUPPLIER' | 'CUSTOMER';
/** The asynchronous outcome hint of a payment (SPEC-0017 BR2; dev/test only). */
export type PaymentOutcome = 'SUCCEEDED' | 'FAILED';

/** The payee of a payout (SPEC-0017). */
export interface Payee {
  id: string;
  type: PayeeType;
}

/** Payout installment view (SPEC-0017 BR6). */
export interface InstallmentView {
  id: string;
  seq: number;
  dueDate: string;
  amount: Money;
  status: PayoutStatus;
  executedAt: string | null;
  proofDocumentId: string | null;
}

/** Payout aggregate view returned by the backend (SPEC-0017). */
export interface PayoutView {
  id: string;
  kind: PayoutKind;
  payee: Payee;
  bookingId: string | null;
  originRef: string | null;
  amount: Money;
  settlementRate: number | null;
  settledBrl: Money | null;
  status: PayoutStatus;
  proofDocumentId: string | null;
  installments: InstallmentView[];
  createdAt: string;
}

/** Body for `POST /api/payouts` (installment plan optional — DL-0050). */
export interface CreatePayoutRequest {
  kind: PayoutKind;
  payee: Payee;
  bookingId?: string | null;
  originRef?: string | null;
  amount: Money;
  settlementRate?: number | null;
  installmentCount?: number | null;
}

/** Optional body for `POST /api/payouts/{id}/execute` (mock outcome hint — dev/test). */
export interface ExecutePayoutRequest {
  outcomeHint?: PaymentOutcome | null;
}
