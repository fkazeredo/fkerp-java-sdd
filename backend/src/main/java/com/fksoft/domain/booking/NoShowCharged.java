package com.fksoft.domain.booking;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: a no-show was processed (SPEC-0010 Events, BR6). Carries the fee (or {@code null}
 * when none applied) and whether it was waived by proof of a cancelled flight. Published
 * in-process; future consumers are Finance/Payout. The proof's compliance verification is out of
 * scope here (DL-0023).
 *
 * @param bookingId the booking id
 * @param fee the no-show fee charged, or {@code null} when none (no fee configured, or waived)
 * @param waived whether the fee was waived by valid proof of a cancelled flight
 * @param occurredAt when the no-show was recorded
 */
public record NoShowCharged(UUID bookingId, Money fee, boolean waived, Instant occurredAt) {}
