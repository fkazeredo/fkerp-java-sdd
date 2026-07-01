import { Money } from '../../core/models/api.models';

/** Kind of an administrative supplier (SPEC-0025). */
export type AdminSupplierType = 'UTILITY' | 'SOFTWARE' | 'SERVICE' | 'OTHER';

/** Lifecycle status of an administrative supplier (SPEC-0025). */
export type AdminSupplierStatus = 'ACTIVE' | 'INACTIVE';

/** Recurrence of an administrative contract (SPEC-0025). */
export type AdminRecurrence = 'MONTHLY' | 'YEARLY' | 'OTHER';

/** Kind of a recurring administrative expense (SPEC-0025 BR3). */
export type AdminExpenseKind = 'UTILITY' | 'AUTONOMOUS_SERVICE' | 'SERVICE' | 'OTHER';

/** Read view of an administrative supplier (SPEC-0025). */
export interface AdminSupplierView {
  id: string;
  type: AdminSupplierType;
  identifier: string;
  displayName: string;
  status: AdminSupplierStatus;
  createdAt: string;
}

/** Read view of an administrative contract (SPEC-0025). */
export interface AdminContractView {
  id: string;
  supplierId: string;
  validFrom: string;
  validUntil: string | null;
  recurrence: AdminRecurrence;
  amount: Money | null;
  documentId: string | null;
  createdAt: string;
}

/** Read view of a recurring administrative expense (SPEC-0025 BR3). */
export interface AdminExpenseView {
  id: string;
  supplierId: string;
  period: string;
  amount: Money | null;
  kind: AdminExpenseKind;
  financeEntryId: string | null;
  requiredDocuments: string[];
  createdAt: string;
}

/** A money value in a request. */
export interface MoneyValue {
  amount: number;
  currency: string;
}

/** Body for `POST /api/admin/suppliers` (SPEC-0025 BR1). */
export interface RegisterSupplierRequest {
  type: AdminSupplierType;
  identifier: string;
  displayName: string;
}

/** Body for `POST /api/admin/suppliers/{id}/contracts` (SPEC-0025 BR2). */
export interface RegisterContractRequest {
  validFrom: string;
  validUntil?: string | null;
  recurrence: AdminRecurrence;
  amount?: MoneyValue | null;
  documentId?: string | null;
}

/** Body for `POST /api/admin/expenses` (SPEC-0025 BR3). */
export interface RegisterExpenseRequest {
  supplierId: string;
  period: string;
  amount: MoneyValue;
  kind: AdminExpenseKind;
}

/** Result of the contract-expiry sweep: how many contracts were newly flagged. */
export interface ExpiringSweepResponse {
  flagged: number;
}
