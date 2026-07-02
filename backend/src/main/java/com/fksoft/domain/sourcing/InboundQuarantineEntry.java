package com.fksoft.domain.sourcing;

import com.fksoft.domain.ModuleInternal;
import com.fksoft.domain.money.Money;
import jakarta.persistence.Column;
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
 * A quarantined inbound quotation (SPEC-0009 BR10, DL-0120 — revises DL-0017): an inbound payload
 * that was rejected at the boundary (e.g. unknown account) is <strong>kept</strong>, not lost, so
 * the operator can fix the cause (register the account) and <strong>replay</strong> it — the
 * exception-queue pattern of mature integrations. The external caller still receives the same 422
 * (the wire contract of DL-0017 is unchanged); what changes is that the payload survives for
 * operational recovery. Status is a small state machine ({@code QUARANTINED → REPLAYED |
 * DISCARDED}), so it stays an enum (Fase 18 criterion). Module-internal.
 */
@Entity
@Table(name = "inbound_quarantine")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ModuleInternal
public class InboundQuarantineEntry {

  @Id private UUID id;

  @Column(name = "external_quotation_id")
  private String externalQuotationId;

  private String accountDocument;

  private String productText;

  private BigDecimal priceAmount;
  private String priceCurrency;

  /** The stable reason code (== i18n key) of the original rejection. */
  private String reasonCode;

  @Enumerated(EnumType.STRING)
  private InboundQuarantineStatus status;

  /** The INTEGRATED quote created by a successful replay (null until then). */
  private UUID replayedQuoteId;

  private Instant receivedAt;
  private Instant resolvedAt;
  private String resolvedBy;

  @Version private Long version;

  /**
   * Quarantines a rejected inbound quotation.
   *
   * @param command the translated inbound command that was rejected
   * @param reasonCode the stable rejection reason (i18n key, e.g. {@code
   *     integration.account.not-found})
   * @param now when it was received/quarantined
   * @return a new, persistable quarantine entry in {@code QUARANTINED} status
   */
  public static InboundQuarantineEntry quarantine(
      RegisterInboundQuotationCommand command, String reasonCode, Instant now) {
    InboundQuarantineEntry entry = new InboundQuarantineEntry();
    entry.id = UUID.randomUUID();
    entry.externalQuotationId = command.externalQuotationId();
    entry.accountDocument = command.accountDocument();
    entry.productText = command.productText();
    entry.priceAmount = command.price().amount();
    entry.priceCurrency = command.price().currency();
    entry.reasonCode = reasonCode;
    entry.status = InboundQuarantineStatus.QUARANTINED;
    entry.receivedAt = now;
    return entry;
  }

  /** Rebuilds the domain command carried by this entry (for a replay). */
  public RegisterInboundQuotationCommand toCommand() {
    return new RegisterInboundQuotationCommand(
        externalQuotationId, productText, Money.of(priceAmount, priceCurrency), accountDocument);
  }

  /**
   * Marks this entry replayed, linking the quote the replay created.
   *
   * @throws InboundQuarantineNotPendingException when the entry is not {@code QUARANTINED}
   */
  public void markReplayed(UUID quoteId, Instant when, String actor) {
    requirePending();
    this.status = InboundQuarantineStatus.REPLAYED;
    this.replayedQuoteId = quoteId;
    this.resolvedAt = when;
    this.resolvedBy = actor;
  }

  /**
   * Discards this entry (the operator decided the payload should not enter the system).
   *
   * @throws InboundQuarantineNotPendingException when the entry is not {@code QUARANTINED}
   */
  public void discard(Instant when, String actor) {
    requirePending();
    this.status = InboundQuarantineStatus.DISCARDED;
    this.resolvedAt = when;
    this.resolvedBy = actor;
  }

  private void requirePending() {
    if (status != InboundQuarantineStatus.QUARANTINED) {
      throw new InboundQuarantineNotPendingException();
    }
  }

  /** Read view of this entry. */
  public InboundQuarantineView toView() {
    return new InboundQuarantineView(
        id,
        externalQuotationId,
        accountDocument,
        productText,
        Money.of(priceAmount, priceCurrency),
        reasonCode,
        status,
        replayedQuoteId,
        receivedAt,
        resolvedAt,
        resolvedBy);
  }
}
