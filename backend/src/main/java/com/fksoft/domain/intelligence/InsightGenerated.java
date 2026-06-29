package com.fksoft.domain.intelligence;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: the DSS generated (or refreshed) an insight (SPEC-0013 Events). Published
 * in-process by {@code intelligence}; consumer: notification/UI (alerts the human). It is an
 * advisory signal — it commands nothing (BR2).
 *
 * @param insightId the generated insight id
 * @param type the insight type
 * @param subjectRef the subject reference (e.g. agency id)
 * @param estimatedGain the estimated gain, or {@code null}
 * @param occurredAt when it was generated
 */
public record InsightGenerated(
    UUID insightId, InsightType type, String subjectRef, Money estimatedGain, Instant occurredAt) {}
