package com.fksoft.domain.sourcing.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Idempotency record of a processed inbound quotation (SPEC-0009 BR4): the {@code
 * externalQuotationId} is the primary key, so a re-delivery of the same id maps to the same {@code
 * quoteId} instead of creating a duplicate. Module-internal.
 */
@Entity
@Table(name = "inbound_quotations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboundQuotation {

  @Id private String externalQuotationId;

  private UUID quoteId;

  private UUID accountId;

  private Instant receivedAt;

  /**
   * Records that an external quotation produced a quote (BR4).
   *
   * @param externalQuotationId the external id (idempotency key)
   * @param quoteId the created INTEGRATED quote id
   * @param accountId the resolved account id
   * @param receivedAt when it was processed
   * @return a new, persistable inbound-quotation record
   */
  public static InboundQuotation of(
      String externalQuotationId, UUID quoteId, UUID accountId, Instant receivedAt) {
    InboundQuotation record = new InboundQuotation();
    record.externalQuotationId = externalQuotationId;
    record.quoteId = quoteId;
    record.accountId = accountId;
    record.receivedAt = receivedAt;
    return record;
  }
}
