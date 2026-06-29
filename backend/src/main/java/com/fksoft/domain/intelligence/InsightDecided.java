package com.fksoft.domain.intelligence;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a human recorded a decision on an insight (SPEC-0013 Events / BR4) — the "accepted
 * × rejected" metric. Published in-process by {@code intelligence}; it records the decision and
 * does NOT trigger any automatic action (BR2).
 *
 * @param insightId the decided insight id
 * @param decision the human decision (ACCEPTED/REJECTED/DISMISSED)
 * @param decidedBy who decided
 * @param occurredAt when the decision was recorded
 */
public record InsightDecided(
    UUID insightId, InsightStatus decision, String decidedBy, Instant occurredAt) {}
