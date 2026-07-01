import { Money } from '../../core/models/api.models';

/** Direction of a ledger entry (SPEC-0015 BR1). */
export type LedgerDirection = 'PAYABLE' | 'RECEIVABLE';
/** Lifecycle of a ledger entry (SPEC-0015 BR2). */
export type EntryStatus = 'PROVISIONAL' | 'CONFIRMED' | 'SETTLED';
/** Status of an accounting period (SPEC-0015 BR3). */
export type PeriodStatus = 'OPEN' | 'CLOSING' | 'CLOSED';
/** Counterparty kind (SPEC-0015 BR1). */
export type PartyType = 'AGENCY' | 'AGENT' | 'SUPPLIER' | 'OTHER';
/** Business type of a ledger entry — the Compliance key (SPEC-0015 BR1). */
export type EntryType =
  | 'COMMISSION_RECEIVABLE'
  | 'COMMISSION_PAYABLE'
  | 'PENALTY'
  | 'UTILITY_EXPENSE'
  | 'AUTONOMOUS_SERVICE'
  | 'SUPPLIER_SETTLEMENT'
  | 'REFUND'
  | 'TAX_PAYABLE'
  | 'SERVICE'
  | 'OTHER_EXPENSE';

/** The counterparty of a ledger entry (SPEC-0015). */
export interface Party {
  id: string;
  type: PartyType;
}

/** Ledger entry view returned by the backend (SPEC-0015). */
export interface LedgerEntryView {
  id: string;
  direction: LedgerDirection;
  party: Party;
  amount: Money;
  entryType: EntryType;
  period: string;
  status: EntryStatus;
  documentRef: string | null;
  createdAt: string;
}

/** Accounting period view (SPEC-0015): totals aggregated per currency (DL-0013). */
export interface PeriodView {
  period: string;
  status: PeriodStatus;
  payableTotals: Money[];
  receivableTotals: Money[];
  closedAt: string | null;
}

/** One per-currency balance line of the trial balance (SPEC-0015 BR10, DL-0043). */
export interface CurrencyBalance {
  currency: string;
  payable: number;
  receivable: number;
  net: number;
}

/** Operational trial balance of a period (SPEC-0015): per-currency balances + status counts. */
export interface TrialBalanceView {
  period: string;
  status: PeriodStatus;
  balances: CurrencyBalance[];
  provisionalCount: number;
  confirmedCount: number;
  settledCount: number;
}

/** Body for `POST /api/finance/entries`. */
export interface CreateLedgerEntryRequest {
  direction: LedgerDirection;
  party: Party;
  amount: Money;
  entryType: EntryType;
  period: string;
}
