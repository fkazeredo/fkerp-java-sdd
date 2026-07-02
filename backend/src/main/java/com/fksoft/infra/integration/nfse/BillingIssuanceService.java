package com.fksoft.infra.integration.nfse;

import com.fksoft.domain.billing.BillingMunicipalityRejectedException;
import com.fksoft.domain.billing.BillingNfseWebserviceException;
import com.fksoft.domain.billing.BillingService;
import com.fksoft.domain.billing.CommissionInvoiceView;
import com.fksoft.domain.billing.InvoiceStatus;
import com.fksoft.domain.billing.NfseGateway;
import com.fksoft.domain.billing.NfseIssuance;
import com.fksoft.domain.billing.NfseIssueRequest;
import com.fksoft.domain.billing.NfseTransmissionException;
import com.fksoft.domain.billing.TaxAssessment;
import com.fksoft.domain.compliance.ComplianceService;
import com.fksoft.domain.compliance.DocumentTypeCodes;
import com.fksoft.domain.compliance.DocumentView;
import com.fksoft.domain.compliance.SignedFormatCodes;
import com.fksoft.domain.finance.EntryTypeCodes;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the issuance of a commission invoice (SPEC-0016 BR3/BR5; DL-0046/DL-0047). It lives
 * in {@code infra.integration.nfse} — not in the Billing domain module — so it can call multiple
 * module facades (Billing, Compliance) without creating a cycle between domain modules (infra →
 * domain is allowed; same pattern as the Phase-6 {@code AfdIngestionService}). The Billing domain
 * module stays a leaf: it never imports Finance or Compliance; Finance posts the tax by consuming
 * the {@code CommissionInvoiceIssued} event that {@link BillingService#markIssued} publishes.
 *
 * <p>Flow (all in one transaction, so the {@code documentId} comes back synchronously and the
 * posting is atomic with the issuance):
 *
 * <ol>
 *   <li>compute the taxes for the invoice's base via the configured regime strategy (DL-0044);
 *   <li>sign + transmit to the municipality via the {@link NfseGateway} ACL, classifying failures
 *       (BR7) into 422/502 business exceptions — never a false "issued";
 *   <li>archive the signed NFS-e in the Compliance vault as a COMMISSION_INVOICE document attached
 *       to the commission entry (BR5, DL-0047), satisfying its DocumentRequirement so the month can
 *       close;
 *   <li>record the issuance via {@link BillingService#markIssued} (publishes the event → Finance
 *       posts the ISS/withholdings payable idempotently).
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingIssuanceService {

  private final BillingService billingService;
  private final NfseGateway nfseGateway;
  private final ComplianceService complianceService;

  /**
   * Issues the NFS-e for a draft commission invoice (idempotent per invoice — re-issuing an already
   * EMITIDA invoice returns it unchanged via {@link BillingService#markIssued}).
   *
   * @param invoiceId the draft invoice id
   * @param actor who issues it (audit)
   * @return the issued invoice view (with number, verification code, ISS and document id)
   * @throws com.fksoft.domain.billing.BillingInvoiceNotFoundException when the invoice does not
   *     exist
   * @throws BillingMunicipalityRejectedException when the municipality rejects (BR7) → 422
   * @throws BillingNfseWebserviceException when the webservice fails (BR7) → 502
   */
  @Transactional
  public CommissionInvoiceView issue(UUID invoiceId, String actor) {
    CommissionInvoiceView invoice = billingService.getById(invoiceId);
    if (invoice.status() == InvoiceStatus.EMITIDA) {
      return invoice; // idempotent no-op (BR4) — already issued, do not re-transmit/re-archive
    }

    TaxAssessment assessment = billingService.assessTaxes(invoiceId);

    NfseIssuance issuance;
    try {
      issuance =
          nfseGateway.issue(
              new NfseIssueRequest(
                  invoiceId,
                  invoice.base(),
                  assessment.iss(),
                  invoice.municipality(),
                  invoice.serviceCode()));
    } catch (NfseTransmissionException failure) {
      // BR7: classify — never a false "issued". REJECTED → 422; TIMEOUT/UNAVAILABLE → 502.
      switch (failure.failureClass()) {
        case REJECTED -> throw new BillingMunicipalityRejectedException(failure.getMessage());
        case TIMEOUT, UNAVAILABLE ->
            throw new BillingNfseWebserviceException(failure.failureClass());
        default -> throw new BillingNfseWebserviceException(failure.failureClass());
      }
    }

    // BR5/DL-0047: archive the signed NFS-e in the vault via the Compliance facade and attach it to
    // the commission entry. Archived as COMMISSION_INVOICE — the document type the
    // COMMISSION_RECEIVABLE entry requires (V8/DL-0012): a commission NFS-e IS the "NF de comissão"
    // the requirement expects, so the archive satisfies the DocumentRequirement (the month can
    // close).
    DocumentView document =
        complianceService.upload(
            DocumentTypeCodes.COMMISSION_INVOICE,
            issuance.signedDocument(),
            "nfse-comissao-" + invoice.number() + ".xml",
            issuance.contentType(),
            LocalDate.now(),
            SignedFormatCodes.XADES,
            true, // the NFS-e carries the taker's CNPJ — personal/tax data (LGPD; access audited)
            invoice.commissionEntryId(),
            EntryTypeCodes.COMMISSION_RECEIVABLE,
            actor);

    CommissionInvoiceView issued =
        billingService.markIssued(
            invoiceId,
            issuance.number(),
            issuance.verificationCode(),
            assessment,
            document.id(),
            actor);
    log.info(
        "CommissionInvoiceIssuanceCompleted invoiceId={} number={} documentId={}",
        invoiceId,
        issued.number(),
        document.id());
    return issued;
  }

  /**
   * Cancels an issued invoice (BR6): cancels at the municipality via the ACL, then records the
   * cancellation in Billing (which publishes {@code CommissionInvoiceCancelled} and frees the
   * commission for a re-issue). The fiscal document already archived in the vault is preserved
   * (legal retention); the contabilistic reversal in Finance is out of scope for SPEC-0016
   * (DL-0047).
   *
   * @param invoiceId the issued invoice id
   * @param reason the cancellation reason
   * @param actor who cancels it (audit)
   * @return the cancelled invoice view
   * @throws com.fksoft.domain.billing.BillingInvoiceNotFoundException when the invoice does not
   *     exist
   * @throws com.fksoft.domain.billing.BillingInvoiceTransitionInvalidException when not EMITIDA
   *     (BR6)
   * @throws BillingNfseWebserviceException when the municipal cancel fails (BR7) → 502
   */
  @Transactional
  public CommissionInvoiceView cancel(UUID invoiceId, String reason, String actor) {
    CommissionInvoiceView invoice = billingService.getById(invoiceId);
    try {
      nfseGateway.cancel(
          new com.fksoft.domain.billing.NfseCancellation(invoiceId, invoice.number(), reason));
    } catch (NfseTransmissionException failure) {
      throw new BillingNfseWebserviceException(failure.failureClass());
    }
    return billingService.cancel(invoiceId, reason, actor);
  }
}
