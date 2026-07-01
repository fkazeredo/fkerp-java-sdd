/**
 * Models for the Cadastro feature (SPEC-0031; ADR-0019/DL-0115): the editable reference-data
 * registry. A cadastro type groups codes; each item is a code (immutable), a pt-BR label, an active
 * flag and a display order. The backend is the authority — the frontend only renders and edits.
 */

/** The set of convertible reference-data types (mirrors the backend `CadastroType`). */
export type CadastroType =
  | 'ADMIN_EXPENSE_KIND'
  | 'ADMIN_RECURRENCE'
  | 'ADMIN_SUPPLIER_TYPE'
  | 'ASSET_TYPE'
  | 'WITHHOLDING_KIND'
  | 'TAX_REGIME'
  // Slice 18b (DL-0116): Marketing / Intelligence / Portfolio.
  | 'CONSENT_PURPOSE'
  | 'MARKETING_SUBJECT_TYPE'
  | 'INSIGHT_SUBJECT_KIND'
  | 'INSIGHT_TYPE'
  | 'INSIGHT_VERDICT'
  | 'GOAL_METRIC';

/** Read view of a cadastro item (`GET /api/cadastro/items`). */
export interface CadastroItemView {
  id: string;
  type: CadastroType;
  code: string;
  label: string;
  active: boolean;
  sortOrder: number;
  createdAt: string;
}

/** Body for `POST /api/cadastro/items` (SPEC-0031 BR1). */
export interface CreateCadastroItemRequest {
  type: CadastroType;
  code: string;
  label: string;
  sortOrder: number;
}

/** Body for `PUT /api/cadastro/items/{id}` (SPEC-0031 BR2). */
export interface UpdateCadastroItemRequest {
  label: string;
  active: boolean;
  sortOrder: number;
}
