package com.fksoft.domain.aftersales;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Public read view of a {@link com.fksoft.domain.aftersales.SupportCase} (SPEC-0018). The delivery
 * layer returns this record, never the {@code @Entity} (backend.md: entity-free delivery).
 *
 * @param id the case id
 * @param bookingId the referenced booking (value)
 * @param type the case type
 * @param status the workflow status
 * @param summary the human summary, or {@code null}
 * @param openedAt when the case was opened
 * @param firstResponseDueAt the SLA deadline for the first response (BR1/BR4)
 * @param dueAt the SLA deadline for resolution (BR1/BR4)
 * @param breached whether the case has breached its SLA (alert flag, BR4)
 * @param resolvedAt when the case was resolved, or {@code null}
 * @param resolution the resolution outcome, or {@code null}
 * @param linkedPayoutId the id of the Payout REFUND triggered, or {@code null} (BR3)
 * @param reopenCount how many times the case was reopened
 * @param costToServeTotal the accumulated cost-to-serve total (BRL, BR5)
 */
public record SupportCaseView(
    UUID id,
    String bookingId,
    String type,
    SupportCaseStatus status,
    String summary,
    Instant openedAt,
    Instant firstResponseDueAt,
    Instant dueAt,
    boolean breached,
    Instant resolvedAt,
    String resolution,
    UUID linkedPayoutId,
    int reopenCount,
    Money costToServeTotal) {}
