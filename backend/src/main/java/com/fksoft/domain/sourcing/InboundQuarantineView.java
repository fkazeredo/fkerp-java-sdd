package com.fksoft.domain.sourcing;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/** Read view of a quarantined inbound quotation (SPEC-0009 BR10, DL-0120). */
public record InboundQuarantineView(
    UUID id,
    String externalQuotationId,
    String accountDocument,
    String productText,
    Money price,
    String reasonCode,
    InboundQuarantineStatus status,
    UUID replayedQuoteId,
    Instant receivedAt,
    Instant resolvedAt,
    String resolvedBy) {}
