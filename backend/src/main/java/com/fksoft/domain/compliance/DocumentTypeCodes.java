package com.fksoft.domain.compliance;

/**
 * The {@code DOCUMENT_TYPE} code constants whose behavior the domain wires (SPEC-0031 BR5;
 * DL-0117). After {@code DocumentType} became an editable cadastro, the type still drives two wired
 * rules (SPEC-0008 BR2/BR4): the legal retention deadline ({@link RetentionPolicy} maps a type to
 * FISCAL or CONTRACT retention) and which entry types a document satisfies (the seeded {@code
 * document_requirements}). The two legal time-record types also gate the AFD/AEJ ingestion path
 * (SPEC-0012 BR4/DL-0029). These constants keep the wired types stable; a new type created in the
 * cadastro flows as pure reference data and falls back to the safe FISCAL retention (documented in
 * {@link RetentionPolicy}). The cadastro owns the extensible set + labels.
 */
public final class DocumentTypeCodes {

  /** Nota Fiscal eletrônica (fiscal retention). */
  public static final String NFE = "NFE";

  /** Nota Fiscal de Serviço eletrônica (fiscal retention). */
  public static final String NFSE = "NFSE";

  /** Recibo de Pagamento a Autônomo (fiscal retention). */
  public static final String RPA = "RPA";

  /** Utility bill (fiscal retention). */
  public static final String UTILITY_BILL = "UTILITY_BILL";

  /** Loan contract (10-year contract retention). */
  public static final String LOAN_CONTRACT = "LOAN_CONTRACT";

  /** Commission invoice / NFS-e de comissão (fiscal retention; satisfies COMMISSION_* entries). */
  public static final String COMMISSION_INVOICE = "COMMISSION_INVOICE";

  /** Payment proof / receipt (fiscal retention). */
  public static final String PAYMENT_PROOF = "PAYMENT_PROOF";

  /** Refund proof / receipt (fiscal retention). */
  public static final String REFUND_PROOF = "REFUND_PROOF";

  /** Payroll / folha (fiscal retention; carries personal data). */
  public static final String PAYROLL = "PAYROLL";

  /** Signed legal time record AFD (fiscal retention; the AFD ingestion path). */
  public static final String TIME_RECORD_AFD = "TIME_RECORD_AFD";

  /** Processed journal AEJ (fiscal retention; the AFD ingestion path). */
  public static final String PROCESSED_JOURNAL_AEJ = "PROCESSED_JOURNAL_AEJ";

  /** Voucher (fiscal retention). */
  public static final String VOUCHER = "VOUCHER";

  /** Representation contract (10-year contract retention). */
  public static final String REPRESENTATION_CONTRACT = "REPRESENTATION_CONTRACT";

  /** Any other supporting document (fiscal retention — the safe default). */
  public static final String OTHER = "OTHER";

  private DocumentTypeCodes() {}
}
