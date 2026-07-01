package com.fksoft.domain.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Business fact: a cancellation produced charges (SPEC-0010 Events). Published in-process; future
 * consumers are Finance (posts AP/AR), Payout (refund) and Intelligence (open merchant exposure,
 * OVERVIEW 8.2-G). The charges are distinct facts that do not net out (BR5/BR11).
 *
 * @param bookingId the cancelled booking id
 * @param charges the resulting charges
 * @param policyType the cancellation-type cadastro code of the frozen policy applied
 * @param occurredAt when the cancellation happened
 */
public record CancellationCharged(
    UUID bookingId, List<Charge> charges, String policyType, Instant occurredAt) {}
