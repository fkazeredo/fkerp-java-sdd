package com.fksoft.domain.sourcing;

import com.fksoft.domain.money.Money;
import java.util.UUID;

/**
 * Result of processing an inbound quotation (SPEC-0009): the created (or, on a re-delivery, the
 * already-existing) INTEGRATED quote. {@code priceOrigin} is always {@code INTEGRATED}.
 *
 * @param quoteId the created/existing quote id
 * @param priceOrigin always {@code "INTEGRATED"}
 * @param appliedAmount the applied amount (the trusted external price)
 */
public record InboundQuotationResult(UUID quoteId, String priceOrigin, Money appliedAmount) {}
