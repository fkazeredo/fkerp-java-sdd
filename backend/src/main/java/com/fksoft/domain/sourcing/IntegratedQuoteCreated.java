package com.fksoft.domain.sourcing;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an INTEGRATED quote was created from an inbound quotation, via the ACL
 * (SPEC-0009). Published in-process by the sourcing module after the quote is created. Future
 * consumer: Intelligence (funnel by channel, 8.2-F) — not present yet, so there is no consumer
 * today. Becomes a stable contract/outbox once another module or service consumes it.
 *
 * @param quoteId the created quote id
 * @param externalQuotationId the external quotation id that produced it
 * @param occurredAt when the quote was created
 */
public record IntegratedQuoteCreated(
    UUID quoteId, String externalQuotationId, Instant occurredAt) {}
