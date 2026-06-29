package com.fksoft.domain.aftersales;

import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an after-sales case missed its SLA deadline (SPEC-0018 BR4 Events). Published
 * in-process by the breach-detection job when {@code now > dueAt} and the case is not resolved. It
 * is an <strong>alert</strong> — it never blocks the operation (the case keeps moving). Consumed by
 * Intelligence and notification.
 *
 * @param caseId the breached case id
 * @param dueAt the deadline that was missed
 * @param occurredAt when the breach was detected
 */
public record SlaBreached(UUID caseId, Instant dueAt, Instant occurredAt) {}
