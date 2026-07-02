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

  // The retention table keyed by the document-type cadastro code (was DocumentType; SPEC-0031/
  // DL-0117). Only the two contract types get 10 years; every other seeded type — and any new code
  // created in the cadastro (dado puro) — falls back to the safe 5-year FISCAL default below.
  private static final Map<String, Period> RETENTION =
      Map.ofEntries(
          Map.entry(DocumentTypeCodes.NFE, FISCAL),
          Map.entry(DocumentTypeCodes.NFSE, FISCAL),
          Map.entry(DocumentTypeCodes.RPA, FISCAL),
          Map.entry(DocumentTypeCodes.UTILITY_BILL, FISCAL),
          Map.entry(DocumentTypeCodes.COMMISSION_INVOICE, FISCAL),
          Map.entry(DocumentTypeCodes.PAYMENT_PROOF, FISCAL),
          Map.entry(DocumentTypeCodes.REFUND_PROOF, FISCAL),
          Map.entry(DocumentTypeCodes.PAYROLL, FISCAL),
          Map.entry(DocumentTypeCodes.TIME_RECORD_AFD, FISCAL),
          Map.entry(DocumentTypeCodes.PROCESSED_JOURNAL_AEJ, FISCAL),
          Map.entry(DocumentTypeCodes.VOUCHER, FISCAL),
          Map.entry(DocumentTypeCodes.OTHER, FISCAL),
          Map.entry(DocumentTypeCodes.LOAN_CONTRACT, CONTRACT),
          Map.entry(DocumentTypeCodes.REPRESENTATION_CONTRACT, CONTRACT));

  private RetentionPolicy() {}

  /**
   * The legal retention deadline for a document of the given type issued on {@code issuedAt} (BR2).
   *
   * @param type the document-type cadastro code
   * @param issuedAt the issue date
   * @return the date until which the document must be kept (purge is rejected before it, BR7)
   */
  public static LocalDate retentionUntil(String type, LocalDate issuedAt) {
    Period period = RETENTION.getOrDefault(type, FISCAL);
    return issuedAt.plus(period);
  }
}
