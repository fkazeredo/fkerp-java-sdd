package com.fksoft.domain.compliance;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;

/**
 * Legal retention table (SPEC-0008 BR2; redesign 7.7), as a domain value: it computes a document's
 * {@code retentionUntil} from its issue date and type. The table is system data (the legal
 * minimums) — fiscal/folha/ponto 5 years (CTN 173/174; trabalhista/previdenciário); contracts 10
 * years (conservative, CC 205). The deadline is computed at ingestion (BR2) and is an aggregate
 * invariant.
 */
public final class RetentionPolicy {

  private static final Period FISCAL = Period.ofYears(5);
  private static final Period CONTRACT = Period.ofYears(10);

  private static final Map<DocumentType, Period> RETENTION =
      Map.ofEntries(
          Map.entry(DocumentType.NFE, FISCAL),
          Map.entry(DocumentType.NFSE, FISCAL),
          Map.entry(DocumentType.RPA, FISCAL),
          Map.entry(DocumentType.UTILITY_BILL, FISCAL),
          Map.entry(DocumentType.COMMISSION_INVOICE, FISCAL),
          Map.entry(DocumentType.PAYMENT_PROOF, FISCAL),
          Map.entry(DocumentType.REFUND_PROOF, FISCAL),
          Map.entry(DocumentType.PAYROLL, FISCAL),
          Map.entry(DocumentType.TIME_RECORD_AFD, FISCAL),
          Map.entry(DocumentType.PROCESSED_JOURNAL_AEJ, FISCAL),
          Map.entry(DocumentType.VOUCHER, FISCAL),
          Map.entry(DocumentType.OTHER, FISCAL),
          Map.entry(DocumentType.LOAN_CONTRACT, CONTRACT),
          Map.entry(DocumentType.REPRESENTATION_CONTRACT, CONTRACT));

  private RetentionPolicy() {}

  /**
   * The legal retention deadline for a document of the given type issued on {@code issuedAt} (BR2).
   *
   * @param type the document type
   * @param issuedAt the issue date
   * @return the date until which the document must be kept (purge is rejected before it, BR7)
   */
  public static LocalDate retentionUntil(DocumentType type, LocalDate issuedAt) {
    Period period = RETENTION.getOrDefault(type, FISCAL);
    return issuedAt.plus(period);
  }
}
