export type LegalType = 'CNPJ' | 'MEI' | 'CPF';
export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'INACTIVE';

/** Account resource returned by the backend (SPEC-0002). */
export interface AccountResponse {
  id: string;
  legalType: LegalType;
  documentNumber: string;
  displayName: string;
  status: AccountStatus;
  cadastur: string | null;
  iata: string | null;
  createdAt: string;
}

/** Body for `POST /api/accounts`. */
export interface CreateAccountRequest {
  legalType: LegalType;
  documentNumber: string;
  displayName: string;
  cadastur?: string | null;
  iata?: string | null;
}
