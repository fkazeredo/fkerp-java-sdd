package com.fksoft.domain.sourcing;

import java.time.Instant;

/**
 * Health read-model of the quotation-site connector (SPEC-0009 Observability; redesign: {@code
 * Platform} observes the connector). A simple projection over the inbound-quotation history — how
 * many inbound quotations were processed and when the last one arrived.
 *
 * @param connector the connector name
 * @param status {@code "UP"} (the inbound endpoint is always reachable; this is not an outbound
 *     dependency — DL-0019)
 * @param inboundQuotationsTotal how many inbound quotations have been processed
 * @param lastReceivedAt when the last inbound quotation arrived (nullable if none yet)
 */
public record ConnectorHealthView(
    String connector, String status, long inboundQuotationsTotal, Instant lastReceivedAt) {}
