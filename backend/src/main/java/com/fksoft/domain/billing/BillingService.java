package com.fksoft.domain.billing;

import com.fksoft.domain.billing.internal.CommissionInvoice;
import com.fksoft.domain.billing.internal.CommissionInvoiceRepository;
import com.fksoft.domain.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the Billing module (SPEC-0016): manages the {@link CommissionInvoice}
 * aggregate — creates the draft, reads it, and persists the issuance/cancellation transitions (BR4/
 * BR6). The cross-module orchestration of issuance (tax calc, signing, transmitting to the
 * municipality, archiving in Compliance) lives in the {@code infra.integration.nfse} adapter
 * (DL-0046/ DL-0047), which calls {@link #markIssued} / {@link #cancel} here. This module is a
 * leaf: it depends only on the {@code money}/{@code error} kernels and its own ports — it never
 * imports Finance or Compliance (keeping the module graph acyclic; Finance consumes the {@link
 * CommissionInvoiceIssued} event instead).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

  private final CommissionInvoiceRepository invoices;
  private final TaxRegimeStrategy taxRegimeStrategy;
  private final Clock clock;
  private final ApplicationEventPublisher events;
  private final BillingTaxRegimeConfig regimeConfig;

  /**
   * Creates a draft commission invoice (BR1): the base is the commission (never the gross package).
   * Idempotent per commission (BR4): if a non-cancelled invoice already exists for the commission,
   * it is returned instead of creating a second one (the partial UNIQUE index is the hard guard).
   *
   * @param commissionEntryId the referenced Finance commission entry (value)
   * @param commissionBase the commission (the taxable base)
   * @param municipality the IBGE municipality code of incidence
   * @param serviceCode the municipal service code
   * @param actor who creates it (audit)
   * @return the draft (or the existing non-cancelled) invoice view
   * @throws BillingBaseInvalidException when the base is invalid (BR1)
   */
  @Transactional
  public CommissionInvoiceView createDraft(
      UUID commissionEntryId,
      Money commissionBase,
      String municipality,
      String serviceCode,
      String actor) {
    return invoices
        .findByCommissionEntryIdAndStatusNot(commissionEntryId, InvoiceStatus.CANCELADA)
        .map(CommissionInvoice::toView)
        .orElseGet(
            () -> {
              CommissionInvoice invoice =
                  CommissionInvoice.draft(
                      commissionEntryId,
                      commissionBase,
                      municipality,
                      serviceCode,
                      regimeConfig.regime(),
                      clock.instant(),
                      actor);
              try {
                invoices.saveAndFlush(invoice);
              } catch (DataIntegrityViolationException raced) {
                // A concurrent create won; return the existing live invoice (BR4 idempotency).
                return invoices
                    .findByCommissionEntryIdAndStatusNot(commissionEntryId, InvoiceStatus.CANCELADA)
                    .map(CommissionInvoice::toView)
                    .orElseThrow(() -> raced);
              }
              log.info(
                  "CommissionInvoiceDrafted invoiceId={} commissionEntryId={} base={} regime={}",
                  invoice.id(),
                  commissionEntryId,
                  commissionBase.amount(),
                  regimeConfig.regime());
              return invoice.toView();
            });
  }

  /**
   * Computes the taxes for an invoice's base using the configured regime strategy (DL-0044). Read
   * helper used by the issuance orchestrator; the taxable base is the commission (BR1).
   *
   * @param invoiceId the invoice id
   * @return the tax assessment for the invoice's base/municipality
   * @throws BillingInvoiceNotFoundException when the invoice does not exist
   */
  @Transactional(readOnly = true)
  public TaxAssessment assessTaxes(UUID invoiceId) {
    CommissionInvoice invoice =
        invoices.findById(invoiceId).orElseThrow(BillingInvoiceNotFoundException::new);
    return taxRegimeStrategy.assess(
        invoice.base(), invoice.toView().municipality(), invoice.toView().serviceCode());
  }

  /**
   * Records a successful issuance and publishes {@link CommissionInvoiceIssued} (BR3/BR5). Called
   * by the issuance orchestrator after the municipality returned the number/code and the document
   * was archived. Idempotent: re-issuing an already-EMITIDA invoice returns it unchanged (no second
   * transmit/archive/event).
   *
   * @param invoiceId the invoice id
   * @param number the NFS-e number
   * @param verificationCode the NFS-e verification code
   * @param assessment the computed taxes
   * @param documentId the archived vault document id (Compliance)
   * @param actor who issued it (audit)
   * @return the issued invoice view
   * @throws BillingInvoiceNotFoundException when the invoice does not exist
   */
  @Transactional
  public CommissionInvoiceView markIssued(
      UUID invoiceId,
      String number,
      String verificationCode,
      TaxAssessment assessment,
      UUID documentId,
      String actor) {
    CommissionInvoice invoice =
        invoices.findById(invoiceId).orElseThrow(BillingInvoiceNotFoundException::new);
    if (invoice.status() == InvoiceStatus.EMITIDA) {
      return invoice.toView(); // idempotent no-op (BR4) — already issued
    }
    Instant now = clock.instant();
    invoice.markIssued(number, verificationCode, assessment, documentId, now, actor);
    invoices.save(invoice);
    events.publishEvent(
        new CommissionInvoiceIssued(
            invoice.id(),
            invoice.commissionEntryId(),
            number,
            documentId,
            assessment.iss(),
            assessment.withholdings(),
            invoice.toView().municipality(),
            now));
    log.info(
        "CommissionInvoiceIssued invoiceId={} commissionEntryId={} number={} documentId={} iss={}",
        invoice.id(),
        invoice.commissionEntryId(),
        number,
        documentId,
        assessment.iss().amount());
    return invoice.toView();
  }

  /**
   * Cancels an issued invoice (BR6), publishing {@link CommissionInvoiceCancelled}. Frees the
   * commission for a new invoice (the partial UNIQUE index excludes cancelled invoices).
   *
   * @param invoiceId the invoice id
   * @param reason the cancellation reason
   * @param actor who cancels it (audit)
   * @return the cancelled invoice view
   * @throws BillingInvoiceNotFoundException when the invoice does not exist
   * @throws BillingInvoiceTransitionInvalidException when not EMITIDA (BR6)
   */
  @Transactional
  public CommissionInvoiceView cancel(UUID invoiceId, String reason, String actor) {
    CommissionInvoice invoice =
        invoices.findById(invoiceId).orElseThrow(BillingInvoiceNotFoundException::new);
    Instant now = clock.instant();
    invoice.cancel(reason, now, actor);
    invoices.save(invoice);
    events.publishEvent(new CommissionInvoiceCancelled(invoice.id(), reason, now));
    log.info("CommissionInvoiceCancelled invoiceId={} reason={}", invoice.id(), reason);
    return invoice.toView();
  }

  /**
   * Fetches an invoice by id.
   *
   * @throws BillingInvoiceNotFoundException when the invoice does not exist
   */
  @Transactional(readOnly = true)
  public CommissionInvoiceView getById(UUID invoiceId) {
    return invoices
        .findById(invoiceId)
        .map(CommissionInvoice::toView)
        .orElseThrow(BillingInvoiceNotFoundException::new);
  }
}
