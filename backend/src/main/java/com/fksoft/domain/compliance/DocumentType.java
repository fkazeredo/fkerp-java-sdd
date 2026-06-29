package com.fksoft.domain.compliance;

/**
 * Catalog of supporting-document types (SPEC-0008 BR1; redesign 7.7). The type drives the retention
 * deadline (BR2, via {@link RetentionPolicy}) and which entry types it satisfies (BR4).
 */
public enum DocumentType {
  NFE,
  NFSE,
  RPA,
  UTILITY_BILL,
  LOAN_CONTRACT,
  COMMISSION_INVOICE,
  PAYMENT_PROOF,
  REFUND_PROOF,
  PAYROLL,
  TIME_RECORD_AFD,
  PROCESSED_JOURNAL_AEJ,
  VOUCHER,
  REPRESENTATION_CONTRACT,
  OTHER
}
