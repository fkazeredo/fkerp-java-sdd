package com.fksoft.domain.sourcing;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an offer's provenance was recorded. Published in-process by the sourcing module;
 * no consumer yet (future: Intelligence — funnel by channel, 8.2-F). Becomes a stable
 * contract/outbox once another module or service consumes it (messaging-and-integrations.md).
 *
 * @param offerId the registered offer id
 * @param origin where the offer comes from (offer-origin cadastro code)
 * @param integrationLevel how integrated the source is (integration-level cadastro code)
 * @param occurredAt when the offer was sourced
 */
public record OfferSourced(
    UUID offerId, String origin, String integrationLevel, Instant occurredAt) {}
