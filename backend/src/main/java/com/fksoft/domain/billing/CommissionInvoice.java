package com.fksoft.domain.billing;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Commission invoice aggregate (SPEC-0016): the NFS-e the Acme issues over its commission. The
 * taxable {@code base} is the commission (BR1, DL-0045) — there is deliberately <strong>no field
 * for the gross package</strong>, so the tax can never accidentally fall on the money that merely
 * passes through. It carries the referenced Finance commission entry by value ({@code
 * commissionEntryId}, never an FK) and the lifecycle RASCUNHO→EMITIDA→CANCELADA (BR4/BR6). The tax
 * math lives in a swappable {@link com.fksoft.domain.billing.TaxRegimeStrategy} (DL-0044); the
 * aggregate only records the {@link TaxAssessment} produced. Module-internal.
 */
@Entity
@Table(name = "commission_invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class CommissionInvoice {

  @Id private UUID id;

  private UUID commissionEntryId;

  private BigDecimal baseAmount;
  private String baseCurrency;

  private BigDecimal issAmount;
  private String withholdingsJson;

  @Enumerated(EnumType.STRING)
  private InvoiceStatus status;

  /** The tax-regime cadastro code (was {@code TaxRegime}; SPEC-0031/DL-0115). */
  private String taxRegime;

  private String municipality;
  private String serviceCode;

  private String number;
  private String verificationCode;
  private UUID documentId;

  private Instant createdAt;
  private Instant updatedAt;
  private String createdBy;
  private String updatedBy;

  @Version private Long version;

  /**
   * Opens a draft invoice (BR1): the base is the commission, validated as a non-negative BRL
   * amount.
   *
   * @param commissionEntryId the referenced Finance commission entry (value)
   * @param commissionBase the commission (the taxable base — never the gross package)
   * @param municipality the IBGE municipality code of incidence
   * @param serviceCode the municipal service code
   * @param regime the tax-regime cadastro code to apply (the issuer's regime, DL-0044)
   * @param now creation instant (UTC)
   * @param actor who creates it (audit)
   * @return a new, persistable draft invoice
   * @throws BillingBaseInvalidException when the base is missing, negative or not in BRL (BR1)
   */
  public static CommissionInvoice draft(
      UUID commissionEntryId,
      Money commissionBase,
      String municipality,
      String serviceCode,
      String regime,
      Instant now,
      String actor) {
    if (commissionBase == null || commissionBase.isNegative()) {
      throw new BillingBaseInvalidException();
    }
    CommissionInvoice invoice = new CommissionInvoice();
    invoice.id = UUID.randomUUID();
    invoice.commissionEntryId = commissionEntryId;
    invoice.baseAmount = commissionBase.amount();
    invoice.baseCurrency = commissionBase.currency();
    invoice.municipality = municipality;
    invoice.serviceCode = serviceCode;
    invoice.taxRegime = regime;
    invoice.status = InvoiceStatus.RASCUNHO;
    invoice.createdAt = now;
    invoice.updatedAt = now;
    invoice.createdBy = actor;
    invoice.updatedBy = actor;
    return invoice;
  }

  /**
   * Records the successful issuance (BR3): the municipal number/verification code, the computed
   * taxes and the archived document id. Only a RASCUNHO may be issued (BR4).
   *
   * @param number the NFS-e number returned by the municipality
   * @param verificationCode the NFS-e verification code
   * @param assessment the computed taxes (ISS + withholdings)
   * @param documentId the archived vault document id (Compliance)
   * @param now the issuance instant (UTC)
   * @param actor who issued it (audit)
   * @throws BillingInvoiceTransitionInvalidException when not a RASCUNHO (BR4)
   */
  public void markIssued(
      String number,
      String verificationCode,
      TaxAssessment assessment,
      UUID documentId,
      Instant now,
      String actor) {
    requireTransitionTo(InvoiceStatus.EMITIDA);
    this.number = number;
    this.verificationCode = verificationCode;
    this.issAmount = assessment.iss().amount();
    this.withholdingsJson = WithholdingsCodec.encode(assessment.withholdings());
    this.documentId = documentId;
    this.status = InvoiceStatus.EMITIDA;
    this.updatedAt = now;
    this.updatedBy = actor;
  }

  /**
   * Cancels an issued invoice (BR6), recording the reason. Only an EMITIDA may be cancelled.
   *
   * @param reason the cancellation reason
   * @param now the cancellation instant (UTC)
   * @param actor who cancelled it (audit)
   * @throws BillingInvoiceTransitionInvalidException when not EMITIDA (BR6)
   */
  public void cancel(String reason, Instant now, String actor) {
    requireTransitionTo(InvoiceStatus.CANCELADA);
    this.status = InvoiceStatus.CANCELADA;
    this.updatedAt = now;
    this.updatedBy = actor;
  }

  private void requireTransitionTo(InvoiceStatus target) {
    if (!status.canTransitionTo(target)) {
      throw new BillingInvoiceTransitionInvalidException();
    }
  }

  /** The invoice id. */
  public UUID id() {
    return id;
  }

  /** The commission base as a money value (the taxable base — BR1). */
  public Money base() {
    return Money.of(baseAmount, baseCurrency);
  }

  /** The ISS due as a money value, or {@code null} while a draft. */
  public Money iss() {
    return issAmount == null ? null : Money.of(issAmount, baseCurrency);
  }

  /** The current lifecycle status. */
  public InvoiceStatus status() {
    return status;
  }

  /** The NFS-e number, or {@code null} while not issued. */
  public String number() {
    return number;
  }

  /** The NFS-e verification code, or {@code null} while not issued. */
  public String verificationCode() {
    return verificationCode;
  }

  /** The referenced Finance commission entry id (value). */
  public UUID commissionEntryId() {
    return commissionEntryId;
  }

  /** Projects the aggregate to its public read view. */
  public CommissionInvoiceView toView() {
    return new CommissionInvoiceView(
        id,
        commissionEntryId,
        Money.of(baseAmount, baseCurrency),
        status,
        iss(),
        WithholdingsCodec.decode(withholdingsJson, baseCurrency),
        taxRegime,
        municipality,
        serviceCode,
        number,
        verificationCode,
        documentId,
        createdAt);
  }
}
