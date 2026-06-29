package com.fksoft.domain.payout;

import com.fksoft.domain.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * Business fact: an agent commission repass was executed (SPEC-0017 Events). Published in-process
 * by the payout module when an {@code AGENT_COMMISSION} payout finishes executing. Consumed by
 * Finance (to baixar the COMMISSION_PAYABLE idempotently) and Intelligence. Payout never calls
 * those modules — this event is the only coupling (leaf, acyclic).
 *
 * @param payoutId the executed payout id (the idempotency source ref)
 * @param agentId the paid agent's id (value)
 * @param amount the amount paid
 * @param occurredAt when it was paid
 */
public record AgentCommissionPaid(
    UUID payoutId, String agentId, Money amount, Instant occurredAt) {}
