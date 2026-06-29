package com.fksoft.domain.sourcing;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Read view of a sourced offer, returned to the delivery layer (the entity never leaves the
 * module).
 *
 * @param id offer id
 * @param productText free-text product description
 * @param basePrice base price in the supplier's currency
 * @param origin where the offer comes from
 * @param integrationLevel how integrated the source is
 * @param externalRef optional external reference
 * @param createdAt when the offer was registered
 */
public record SourcedOfferView(
    UUID id,
    String productText,
    Money basePrice,
    OfferOrigin origin,
    IntegrationLevel integrationLevel,
    String externalRef,
    Instant createdAt) {}
