/** Catalog of supporting-document types (SPEC-0008 BR1). */
export type DocumentType =
  | 'NFE'
  | 'NFSE'
  | 'RPA'
  | 'UTILITY_BILL'
  | 'LOAN_CONTRACT'
  | 'COMMISSION_INVOICE'
  | 'PAYMENT_PROOF'
  | 'REFUND_PROOF'
  | 'PAYROLL'
  | 'TIME_RECORD_AFD'
  | 'PROCESSED_JOURNAL_AEJ'
  | 'VOUCHER'
  | 'REPRESENTATION_CONTRACT'
  | 'OTHER';

/** Signed-document format (SPEC-0008 BR3); null when not signed. */
export type SignedFormat = 'CAdES_P7S' | 'XADES' | 'PADES';

/** Vault document view returned by the backend (SPEC-0008). fileRef is intentionally not exposed. */
export interface DocumentView {
  id: string;
  type: DocumentType;
  hash: string;
  issuedAt: string;
  retentionUntil: string;
  signedFormat: SignedFormat | null;
  hasPersonalData: boolean;
  createdAt: string;
}

/** A ledger entry that blocks the monthly close because a document is missing (SPEC-0015 BR3). */
export interface PendingEntry {
  entryId: string;
  entryType: string;
  missing: string[];
}

/** Result of the period close-check (SPEC-0008): whether it may close and the blocking entries. */
export interface CloseCheckView {
  period: string;
  canClose: boolean;
  pending: PendingEntry[];
}

/** Fields for the multipart `POST /api/compliance/documents` upload. */
export interface UploadDocumentRequest {
  file: File;
  type: DocumentType;
  issuedAt: string;
  signedFormat?: SignedFormat | null;
  hasPersonalData: boolean;
}
