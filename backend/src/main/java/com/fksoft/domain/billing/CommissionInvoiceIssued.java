package com.fksoft.domain.billing;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Business fact: a commission invoice (NFS-e) was issued (SPEC-0016 Events). Published in-process
 * by {@link BillingService}; consumed by Finance (to post the ISS/withholdings payable idempotently
 * — DL-0047) and Intelligence. It carries the computed taxes so the Finance consumer can post
 * without reading back into Billing — Billing never calls Finance, this event is the only coupling,
 * keeping the module graph acyclic.
 *
 * @param invoiceId the issued invoice id
 * @param commissionEntryId the referenced Finance commission entry (value)
 * @param number the municipal NFS-e number
 * @param documentId the archived vault document id (Compliance)
 * @param iss the ISS due (PAYABLE — a tax to remit)
 * @param withholdings the withholdings due (empty under Simples Nacional)
 * @param municipality the IBGE municipality code (the ISS counterparty reference)
 * @param occurredAt when it was issued
 */
public record CommissionInvoiceIssued(
    UUID invoiceId,
    UUID commissionEntryId,
    String number,
    UUID documentId,
    Money iss,
    List<Withholding> withholdings,
    String municipality,
    Instant occurredAt) {

  public CommissionInvoiceIssued {
    withholdings = withholdings == null ? List.of() : List.copyOf(withholdings);
  }
}
